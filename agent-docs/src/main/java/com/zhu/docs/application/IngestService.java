package com.zhu.docs.application;

import com.zhu.common.util.UtcTimes;
import com.zhu.docs.api.DocsEmbedder;
import com.zhu.docs.api.DocsIngest;
import com.zhu.docs.api.DocsSyncStatus;
import com.zhu.docs.infrastructure.Chunker;
import com.zhu.docs.infrastructure.DocChunk;
import com.zhu.docs.infrastructure.FsSourceReader;
import com.zhu.docs.infrastructure.MarkdownDocument;
import com.zhu.docs.infrastructure.MarkdownParser;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 全量同步。MySQL 先落正文，向量失败记 PARTIAL，下次即使哈希没变也补缺的块。
 * 根目录不存在的仓库不参与删除，避免配错路径把索引清掉。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@ConditionalOnBean(DocsVectors.class)
public class IngestService implements DocsIngest {

    private static final Logger LOG = Logger.getLogger(IngestService.class.getName());

    /** 嵌入失败时连试这几次。嵌入器自己已经有超时，这里不再睡觉。 */
    static final int EMBED_ATTEMPTS = 3;

    private static final int TITLE_LIMIT = 255;

    private static final int NOTE_LIMIT = 512;

    private final DocsProperties properties;

    private final DocsCatalog catalog;

    private final DocsVectors vectors;

    private final DocsEmbedder embedder;

    private final FsSourceReader reader;

    private final MarkdownParser parser;

    private final Chunker chunker;

    private final DocsSyncGate gate;

    private final Executor executor;

    /**
     * 默认用单独的后台线程，接口马上返回 runId。
     * 类里还有一个给测试用的构造器，不标这个的话 Spring 会去找无参构造，进程起不来。
     */
    @Autowired
    public IngestService(
            DocsProperties properties,
            DocsCatalog catalog,
            DocsVectors vectors,
            DocsEmbedder embedder) {
        this(properties, catalog, vectors, embedder, new FsSourceReader(), new MarkdownParser(), new Chunker(),
                new DocsSyncGate(), defaultExecutor());
    }

    /**
     * 测试可以换执行器。传入的执行器若同步执行，单飞锁要等这一轮结束才放开。
     */
    IngestService(
            DocsProperties properties,
            DocsCatalog catalog,
            DocsVectors vectors,
            DocsEmbedder embedder,
            FsSourceReader reader,
            MarkdownParser parser,
            Chunker chunker,
            DocsSyncGate gate,
            Executor executor) {
        this.properties = properties;
        this.catalog = catalog;
        this.vectors = vectors;
        this.embedder = embedder;
        this.reader = reader;
        this.parser = parser;
        this.chunker = chunker;
        this.gate = gate;
        this.executor = executor;
    }

    /**
     * 已有一轮就返回它的 id。新一轮先落 RUNNING，再丢到执行器。
     */
    @Override
    public synchronized long start() {
        Long existing = gate.currentIfRunning();
        if (existing != null) {
            return existing;
        }
        long id = catalog.openRun();
        gate.markRunning(id);
        try {
            executor.execute(() -> execute(id));
        } catch (RuntimeException ex) {
            gate.finish();
            catalog.closeRun(id, 0, 0, 0, 0, "FAILED", clip("start failed", NOTE_LIMIT));
            throw ex;
        }
        return id;
    }

    /** 最近一轮。没有记录时是 NONE。 */
    @Override
    public DocsSyncStatus status() {
        return catalog.latestRun().orElseGet(DocsSyncStatus::none);
    }

    /** 跑完一轮并写上结束状态。异常收成 FAILED，不抛出同步线程。 */
    private void execute(long id) {
        try {
            Outcome outcome = runOnce();
            catalog.closeRun(id, outcome.scanned, outcome.added, outcome.updated, outcome.removed,
                    outcome.status, outcome.note());
            LOG.info("docs sync finished id=" + id + " status=" + outcome.status
                    + " scanned=" + outcome.scanned + " added=" + outcome.added
                    + " updated=" + outcome.updated + " removed=" + outcome.removed);
        } catch (RuntimeException ex) {
            catalog.closeRun(id, 0, 0, 0, 0, "FAILED", clip("sync failed", NOTE_LIMIT));
            LOG.warning("docs sync failed id=" + id);
        } finally {
            gate.finish();
        }
    }

    /**
     * 扫已配置且目录存在的仓库。同一次里读失败的路径仍算「还在」，避免误删。
     */
    Outcome runOnce() {
        Outcome outcome = new Outcome();
        Set<String> seen = new HashSet<>();
        Set<String> scannedRepos = new HashSet<>();
        for (DocsProperties.Source source : properties.getSources()) {
            if (source.getRepo() == null || source.getRepo().isBlank()) {
                continue;
            }
            Path root = Path.of(source.getRoot() == null ? "" : source.getRoot());
            if (!Files.isDirectory(root)) {
                outcome.addNote("missing root " + source.getRepo());
                outcome.partial = true;
                continue;
            }
            scannedRepos.add(source.getRepo());
            List<FsSourceReader.SourceFile> files = reader.list(root, source.getInclude(), source.getExclude());
            for (FsSourceReader.SourceFile file : files) {
                outcome.scanned++;
                String key = source.getRepo() + "\0" + file.relativePath();
                seen.add(key);
                try {
                    String markdown = Files.readString(file.absolutePath(), StandardCharsets.UTF_8);
                    Change change = ingestFile(source.getRepo(), file.relativePath(), markdown);
                    if (change == Change.ADDED || change == Change.ADDED_PARTIAL) {
                        outcome.added++;
                    } else if (change == Change.UPDATED || change == Change.UPDATED_PARTIAL) {
                        outcome.updated++;
                    }
                    if (change == Change.ADDED_PARTIAL || change == Change.UPDATED_PARTIAL) {
                        outcome.partial = true;
                        outcome.embedFailures++;
                    }
                } catch (IOException | RuntimeException ex) {
                    outcome.partial = true;
                    outcome.readFailures++;
                }
            }
        }
        for (String repo : scannedRepos) {
            for (CatalogDocument existing : catalog.listActive(repo)) {
                String key = repo + "\0" + existing.path();
                if (seen.contains(key)) {
                    continue;
                }
                deleteVectors(existing);
                catalog.markDeleted(existing.id());
                outcome.removed++;
            }
        }
        if (outcome.embedFailures > 0) {
            outcome.addNote("embed failed=" + outcome.embedFailures);
        }
        if (outcome.readFailures > 0) {
            outcome.addNote("read failed=" + outcome.readFailures);
        }
        outcome.status = outcome.partial ? "PARTIAL" : "SUCCESS";
        return outcome;
    }

    /**
     * 哈希没变且每块向量都在时什么都不写。否则先写 MySQL，再补向量。
     */
    private Change ingestFile(String repo, String path, String markdown) {
        MarkdownDocument parsed = parser.parse(markdown);
        List<DocChunk> chunks = chunker.chunk(markdown);
        String hash = DocsIds.contentHash(markdown);
        String id = DocsIds.documentId(repo, path);
        Optional<CatalogDocument> existing = catalog.findByRepoPath(repo, path);
        if (existing.isPresent() && unchanged(existing.get(), hash)) {
            return Change.UNCHANGED;
        }
        // 已删除的同一路径再次出现算新增。id 仍是原来的。
        boolean created = existing.isEmpty() || existing.get().deleted();
        String title = clip(titleOf(parsed, path), TITLE_LIMIT);
        String topic = DocsTopicClassifier.topic(repo, path);
        CatalogDocument document = new CatalogDocument(
                id,
                repo,
                path,
                title,
                topic,
                DocsTopicClassifier.docType(path),
                properties.visibility(topic),
                repo,
                hash,
                markdown,
                UtcTimes.now(),
                false,
                List.of());
        List<CatalogChunk> stored = new ArrayList<>();
        List<String> previousIds = new ArrayList<>();
        if (existing.isPresent()) {
            for (CatalogChunk chunk : existing.get().chunks()) {
                previousIds.add(chunk.id());
            }
        }
        for (DocChunk chunk : chunks) {
            String chunkId = DocsIds.chunkId(id, chunk.ordinal());
            stored.add(new CatalogChunk(
                    chunkId,
                    clip(chunk.heading(), TITLE_LIMIT),
                    clip(chunk.anchor(), TITLE_LIMIT),
                    chunk.ordinal(),
                    DocsIds.contentHash(chunk.text()),
                    chunk.text(),
                    null));
        }
        catalog.save(document, stored);
        Set<String> keep = new HashSet<>();
        boolean embedFailed = false;
        for (CatalogChunk chunk : stored) {
            keep.add(chunk.id());
            float[] vector = embed(chunk.text());
            if (vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                embedFailed = true;
                continue;
            }
            vectors.upsert(chunk.id(), vector, id, topic, document.visibility(), repo);
            catalog.markEmbedded(chunk.id(), chunk.textHash());
        }
        List<String> stale = new ArrayList<>();
        for (String previous : previousIds) {
            if (!keep.contains(previous)) {
                stale.add(previous);
            }
        }
        vectors.delete(stale);
        if (embedFailed) {
            return created ? Change.ADDED_PARTIAL : Change.UPDATED_PARTIAL;
        }
        return created ? Change.ADDED : Change.UPDATED;
    }

    /**
     * 正文哈希一致，且每一块的 embeddedHash 对得上、Milvus 里也有这条 id。
     * 已删除的行不算没变，回来时要重新写入。
     */
    private boolean unchanged(CatalogDocument existing, String hash) {
        if (existing.deleted()) {
            return false;
        }
        if (existing.contentHash() == null || !existing.contentHash().equals(hash)) {
            return false;
        }
        if (existing.chunks().isEmpty()) {
            return false;
        }
        List<String> ids = new ArrayList<>();
        for (CatalogChunk chunk : existing.chunks()) {
            if (chunk.embeddedHash() == null || !chunk.embeddedHash().equals(chunk.textHash())) {
                return false;
            }
            ids.add(chunk.id());
        }
        return vectors.present(ids).containsAll(ids);
    }

    /** 空向量再试。三次都空就交给调用方记 PARTIAL。 */
    private float[] embed(String text) {
        float[] vector = new float[0];
        for (int attempt = 0; attempt < EMBED_ATTEMPTS; attempt++) {
            vector = embedder.embed(text);
            if (vector != null && vector.length == MilvusCollections.DEFAULT_DIMENSIONS) {
                return vector;
            }
        }
        return new float[0];
    }

    private void deleteVectors(CatalogDocument document) {
        List<String> ids = new ArrayList<>();
        for (CatalogChunk chunk : document.chunks()) {
            ids.add(chunk.id());
        }
        vectors.delete(ids);
    }

    private static String titleOf(MarkdownDocument parsed, String path) {
        if (parsed.title() != null && !parsed.title().isBlank()) {
            return parsed.title().trim();
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".md")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static Executor defaultExecutor() {
        return command -> {
            Thread thread = new Thread(command, "docs-sync");
            thread.setDaemon(true);
            thread.start();
        };
    }

    private enum Change {
        UNCHANGED,
        ADDED,
        UPDATED,
        ADDED_PARTIAL,
        UPDATED_PARTIAL
    }

    /** 一轮的计数。note 只记数量，不记路径和正文。 */
    static final class Outcome {
        private int scanned;
        private int added;
        private int updated;
        private int removed;
        private int embedFailures;
        private int readFailures;
        private boolean partial;
        private String status = "SUCCESS";
        private final StringBuilder notes = new StringBuilder();

        int scanned() {
            return scanned;
        }

        int added() {
            return added;
        }

        int updated() {
            return updated;
        }

        int removed() {
            return removed;
        }

        String status() {
            return status;
        }

        private void addNote(String text) {
            if (notes.length() > 0) {
                notes.append("; ");
            }
            notes.append(text);
        }

        private String note() {
            return clip(notes.toString(), NOTE_LIMIT);
        }
    }
}
