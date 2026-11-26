package com.zhu.docs.infrastructure.persistence;

import com.zhu.common.util.UtcTimes;
import com.zhu.docs.api.DocsSyncStatus;
import com.zhu.docs.application.CatalogChunk;
import com.zhu.docs.application.CatalogDocument;
import com.zhu.docs.application.DocsCatalog;
import com.zhu.docs.application.DocsLinkTarget;
import com.zhu.docs.application.DocsSearchChunk;
import com.zhu.docs.application.DocsSearchIndex;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用 MySQL 存文档索引副本。同步可以整表重建，这里不碰源文件。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlDocsCatalog implements DocsCatalog, DocsSearchIndex {

    private static final int NOTE_LIMIT = 512;

    private final DocsDocumentMapper documents;

    private final DocsChunkMapper chunks;

    private final DocsSyncRunMapper runs;

    /** Mapper 只在 MySQL 打开时存在。 */
    public MysqlDocsCatalog(DocsDocumentMapper documents, DocsChunkMapper chunks, DocsSyncRunMapper runs) {
        this.documents = documents;
        this.chunks = chunks;
        this.runs = runs;
    }

    /** 先记 RUNNING，调用方再开始扫描。失败时也有一条可查的记录。 */
    @Override
    public long openRun() {
        DocsSyncRunRow row = new DocsSyncRunRow();
        row.setStartedAt(UtcTimes.now());
        row.setScanned(0);
        row.setAdded(0);
        row.setUpdated(0);
        row.setRemoved(0);
        row.setStatus("RUNNING");
        runs.insert(row);
        return row.getId();
    }

    /** note 超长截断，避免一长串失败把这一行写爆。 */
    @Override
    public void closeRun(long id, int scanned, int added, int updated, int removed, String status, String note) {
        DocsSyncRunRow row = new DocsSyncRunRow();
        row.setId(id);
        row.setFinishedAt(UtcTimes.now());
        row.setScanned(scanned);
        row.setAdded(added);
        row.setUpdated(updated);
        row.setRemoved(removed);
        row.setStatus(status);
        row.setNote(clip(note));
        runs.updateById(row);
    }

    /** 没有同步过时为空。 */
    @Override
    public Optional<DocsSyncStatus> latestRun() {
        DocsSyncRunRow row = runs.latest();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new DocsSyncStatus(
                row.getId() == null ? 0 : row.getId(),
                row.getStartedAt(),
                row.getFinishedAt(),
                zero(row.getScanned()),
                zero(row.getAdded()),
                zero(row.getUpdated()),
                zero(row.getRemoved()),
                row.getStatus(),
                row.getNote()));
    }

    /** 含已删除行，好让同一路径沿用原来的 id。 */
    @Override
    public Optional<CatalogDocument> findByRepoPath(String repo, String path) {
        DocsDocumentRow row = documents.findByRepoPath(repo, path);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toDocument(row, chunks.listByDoc(row.getId()), true));
    }

    /**
     * 文档和小节一起换掉。先删旧小节再插入，避免序号变了还留着旧块。
     */
    @Override
    @Transactional
    public void save(CatalogDocument document, List<CatalogChunk> nextChunks) {
        DocsDocumentRow row = toRow(document);
        if (documents.selectById(document.id()) == null) {
            documents.insert(row);
        } else {
            documents.updateById(row);
        }
        chunks.deleteByDoc(document.id());
        for (CatalogChunk chunk : nextChunks) {
            chunks.insert(toChunk(document.id(), chunk));
        }
    }

    /** 向量已经写上。哈希留在块上，下次哈希没变且 Milvus 还有这条就跳过。 */
    @Override
    public void markEmbedded(String chunkId, String textHash) {
        chunks.markEmbedded(chunkId, textHash);
    }

    /** 只标删除，不回收 id。 */
    @Override
    public void markDeleted(String id) {
        DocsDocumentRow row = new DocsDocumentRow();
        row.setId(id);
        row.setDeleted(1);
        documents.updateById(row);
    }

    /** 这个仓库还在的文档，带小节哈希，不带正文。 */
    @Override
    public List<CatalogDocument> listActive(String repo) {
        List<CatalogDocument> found = new ArrayList<>();
        for (DocsDocumentRow row : documents.listActive(repo)) {
            found.add(toDocument(row, chunks.listByDoc(row.getId()), false));
        }
        return List.copyOf(found);
    }

    /** owner 为 false 时 SQL 已经去掉私有文档。 */
    @Override
    public List<CatalogDocument> listVisible(boolean owner) {
        List<CatalogDocument> found = new ArrayList<>();
        for (DocsDocumentRow row : documents.listVisible(owner ? 1 : 0)) {
            found.add(toDocument(row, List.of(), false));
        }
        return List.copyOf(found);
    }

    /** 看不见时为空。调用方统一成 NOT_FOUND。 */
    @Override
    public Optional<CatalogDocument> findVisible(String id, boolean owner) {
        DocsDocumentRow row = documents.findVisible(id, owner ? 1 : 0);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toDocument(row, chunks.listByDoc(row.getId()), true));
    }

    /**
     * 一次查出同仓可见路径。按文档合并锚点，空锚点行只用来保住没有小节的文档。
     */
    @Override
    public List<DocsLinkTarget> linkTargets(String repo, boolean owner) {
        if (repo == null || repo.isBlank()) {
            return List.of();
        }
        Map<String, List<String>> anchors = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        for (DocsLinkRow row : documents.listLinkTargets(repo, owner ? 1 : 0)) {
            if (row.getId() == null || row.getPath() == null) {
                continue;
            }
            anchors.computeIfAbsent(row.getId(), ignored -> new ArrayList<>());
            paths.putIfAbsent(row.getId(), row.getPath());
            if (row.getAnchor() != null && !row.getAnchor().isBlank()) {
                anchors.get(row.getId()).add(row.getAnchor());
            }
        }
        List<DocsLinkTarget> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : paths.entrySet()) {
            found.add(new DocsLinkTarget(entry.getKey(), entry.getValue(), List.copyOf(anchors.get(entry.getKey()))));
        }
        return List.copyOf(found);
    }

    /** 空 id 不查。看不见的块不会出现在结果里。 */
    @Override
    public List<DocsSearchChunk> chunksByIds(Collection<String> ids, boolean owner) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return toSearchChunks(chunks.listByIds(ids, owner ? 1 : 0));
    }

    /** pattern 由搜索服务转义。这里只执行，不再改问句。 */
    @Override
    public List<DocsSearchChunk> likeChunks(String pattern, String topic, String source, boolean owner, int limit) {
        if (pattern == null || pattern.isBlank() || limit < 1) {
            return List.of();
        }
        return toSearchChunks(chunks.like(
                pattern,
                topic == null ? "" : topic,
                source == null ? "" : source,
                owner ? 1 : 0,
                limit));
    }

    private static List<DocsSearchChunk> toSearchChunks(List<DocsSearchChunkRow> rows) {
        List<DocsSearchChunk> found = new ArrayList<>();
        if (rows == null) {
            return List.of();
        }
        for (DocsSearchChunkRow row : rows) {
            if (row == null || row.getChunkId() == null) {
                continue;
            }
            found.add(new DocsSearchChunk(
                    row.getChunkId(),
                    row.getDocId(),
                    row.getTitle(),
                    row.getHeading(),
                    row.getAnchor(),
                    row.getChunkText(),
                    row.getMarkdown()));
        }
        return List.copyOf(found);
    }

    private static CatalogDocument toDocument(DocsDocumentRow row, List<DocsChunkRow> chunkRows, boolean withText) {
        List<CatalogChunk> stored = new ArrayList<>();
        for (DocsChunkRow chunk : chunkRows) {
            stored.add(new CatalogChunk(
                    chunk.getId(),
                    chunk.getHeading(),
                    chunk.getAnchor(),
                    chunk.getOrdinal() == null ? 0 : chunk.getOrdinal(),
                    chunk.getTextHash(),
                    withText ? chunk.getText() : null,
                    chunk.getEmbeddedHash()));
        }
        return new CatalogDocument(
                row.getId(),
                row.getRepo(),
                row.getPath(),
                row.getTitle(),
                row.getTopic(),
                row.getDocType(),
                row.getVisibility(),
                row.getSource(),
                row.getContentHash(),
                withText ? row.getMarkdown() : null,
                row.getIndexedAt(),
                row.getDeleted() != null && row.getDeleted() != 0,
                List.copyOf(stored));
    }

    private static DocsDocumentRow toRow(CatalogDocument document) {
        DocsDocumentRow row = new DocsDocumentRow();
        row.setId(document.id());
        row.setRepo(document.repo());
        row.setPath(document.path());
        row.setTitle(document.title());
        row.setTopic(document.topic());
        row.setDocType(document.docType());
        row.setVisibility(document.visibility());
        row.setSource(document.source());
        row.setContentHash(document.contentHash());
        row.setMarkdown(document.markdown() == null ? "" : document.markdown());
        row.setIndexedAt(document.indexedAt() == null ? UtcTimes.now() : document.indexedAt());
        row.setDeleted(document.deleted() ? 1 : 0);
        return row;
    }

    private static DocsChunkRow toChunk(String docId, CatalogChunk chunk) {
        DocsChunkRow row = new DocsChunkRow();
        row.setId(chunk.id());
        row.setDocId(docId);
        row.setHeading(chunk.heading());
        row.setAnchor(chunk.anchor());
        row.setOrdinal(chunk.ordinal());
        row.setTextHash(chunk.textHash());
        row.setEmbeddedHash(chunk.embeddedHash());
        row.setText(chunk.text() == null ? "" : chunk.text());
        return row;
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String clip(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() <= NOTE_LIMIT ? trimmed : trimmed.substring(0, NOTE_LIMIT);
    }
}
