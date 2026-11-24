package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.common.exception.ErrorCode;
import com.zhu.common.exception.BusinessException;
import com.zhu.docs.api.DocsEmbedder;
import com.zhu.docs.infrastructure.Chunker;
import com.zhu.docs.infrastructure.FsSourceReader;
import com.zhu.docs.infrastructure.MarkdownParser;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 同步的幂等、缺向量补写，以及私有文档的阅读过滤。不连 MySQL 和 Milvus。 */
class IngestServiceTest {

    /** 连着同步两次，第二次没有新增也没有改写。删掉文件后记成 removed。 */
    @Test
    void secondSyncIsIdleUntilFileDisappears(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/a.md"), "# 标题\n\n## 第一节\n正文\n");
        MemoryCatalog catalog = new MemoryCatalog();
        MemoryVectors vectors = new MemoryVectors();
        IngestService service = service(root, catalog, vectors, text -> vector());
        IngestService.Outcome first = service.runOnce();
        assertThat(first.added()).isEqualTo(1);
        assertThat(first.status()).isEqualTo("SUCCESS");
        IngestService.Outcome second = service.runOnce();
        assertThat(second.added()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.removed()).isZero();
        Files.delete(root.resolve("docs/a.md"));
        IngestService.Outcome third = service.runOnce();
        assertThat(third.removed()).isEqualTo(1);
        assertThat(catalog.visible(false)).isEmpty();
    }

    /** 嵌入失败仍留下正文。下次嵌入恢复后只算更新，并把向量补上。 */
    @Test
    void missingVectorIsRetried(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/a.md"), "# 标题\n\n## 第一节\n正文\n");
        MemoryCatalog catalog = new MemoryCatalog();
        MemoryVectors vectors = new MemoryVectors();
        AtomicInteger calls = new AtomicInteger();
        DocsEmbedder flaky = text -> {
            calls.incrementAndGet();
            return calls.get() <= IngestService.EMBED_ATTEMPTS ? new float[0] : vector();
        };
        IngestService failing = service(root, catalog, vectors, flaky);
        IngestService.Outcome partial = failing.runOnce();
        assertThat(partial.status()).isEqualTo("PARTIAL");
        assertThat(vectors.ids()).isEmpty();
        assertThat(catalog.visible(false)).hasSize(1);
        IngestService.Outcome repaired = failing.runOnce();
        assertThat(repaired.added()).isZero();
        assertThat(repaired.updated()).isEqualTo(1);
        assertThat(repaired.status()).isEqualTo("SUCCESS");
        assertThat(vectors.ids()).hasSize(1);
    }

    /** 已经在跑的一轮再调用，返回同一个 id，不新开记录。 */
    @Test
    void singleFlightReturnsCurrentRun(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("docs"));
        MemoryCatalog catalog = new MemoryCatalog();
        IngestService service = service(root, catalog, new MemoryVectors(), text -> vector(), command -> {
        });
        long first = service.start();
        assertThat(service.start()).isEqualTo(first);
        assertThat(catalog.runs()).isEqualTo(1);
    }

    /** 私有文档对名单外的人和匿名都是不存在。树上始终有七个主题。 */
    @Test
    void privatePageIsHidden() {
        MemoryCatalog catalog = new MemoryCatalog();
        catalog.put(sample("career", "private", "docs/03-面试准备/a.md"));
        catalog.put(sample("architecture", "public", "docs/01-技术架构/a.md"));
        DocsProperties properties = new DocsProperties();
        properties.setOwnerUserIds(List.of("owner-1"));
        DocsQueryService query = new DocsQueryService(catalog, properties);
        assertThat(query.tree(null).topics()).hasSize(7);
        assertThat(query.tree(null).topics().get(0).documents()).hasSize(1);
        assertThat(query.tree("owner-1").topics().get(6).documents()).hasSize(1);
        assertThatThrownBy(() -> query.page("other", "career-id"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(query.page("owner-1", "career-id").title()).isEqualTo("私有");
    }

    private static IngestService service(Path root, MemoryCatalog catalog, MemoryVectors vectors, DocsEmbedder embedder) {
        return service(root, catalog, vectors, embedder, Runnable::run);
    }

    private static IngestService service(
            Path root, MemoryCatalog catalog, MemoryVectors vectors, DocsEmbedder embedder, java.util.concurrent.Executor executor) {
        DocsProperties properties = new DocsProperties();
        DocsProperties.Source source = new DocsProperties.Source();
        source.setRepo("agentscope");
        source.setRoot(root.toString());
        source.setInclude(List.of("docs/**/*.md"));
        properties.setSources(List.of(source));
        return new IngestService(
                properties,
                catalog,
                vectors,
                embedder,
                new FsSourceReader(),
                new MarkdownParser(),
                new Chunker(),
                new DocsSyncGate(),
                executor);
    }

    private static float[] vector() {
        return new float[MilvusCollections.DEFAULT_DIMENSIONS];
    }

    private static CatalogDocument sample(String topic, String visibility, String path) {
        return new CatalogDocument(
                topic + "-id",
                "dream-saas",
                path,
                "private".equals(visibility) ? "私有" : "公开",
                topic,
                "guide",
                visibility,
                "dream-saas",
                "hash",
                "正文",
                null,
                false,
                List.of());
    }

    /** 内存目录。只实现同步和查询用到的行为。 */
    private static final class MemoryCatalog implements DocsCatalog {

        private final Map<String, CatalogDocument> byKey = new HashMap<>();

        private final List<long[]> runs = new ArrayList<>();

        private int nextId = 1;

        private int runCount;

        @Override
        public long openRun() {
            runCount++;
            return nextId++;
        }

        @Override
        public void closeRun(long id, int scanned, int added, int updated, int removed, String status, String note) {
            runs.add(new long[] {id});
        }

        @Override
        public Optional<com.zhu.docs.api.DocsSyncStatus> latestRun() {
            return Optional.empty();
        }

        @Override
        public Optional<CatalogDocument> findByRepoPath(String repo, String path) {
            return Optional.ofNullable(byKey.get(repo + "\0" + path));
        }

        @Override
        public void save(CatalogDocument document, List<CatalogChunk> chunks) {
            byKey.put(document.repo() + "\0" + document.path(), copy(document, chunks, false));
        }

        @Override
        public void markEmbedded(String chunkId, String textHash) {
            for (CatalogDocument document : new ArrayList<>(byKey.values())) {
                List<CatalogChunk> next = new ArrayList<>();
                boolean changed = false;
                for (CatalogChunk chunk : document.chunks()) {
                    if (chunk.id().equals(chunkId)) {
                        next.add(new CatalogChunk(
                                chunk.id(), chunk.heading(), chunk.anchor(), chunk.ordinal(),
                                chunk.textHash(), chunk.text(), textHash));
                        changed = true;
                    } else {
                        next.add(chunk);
                    }
                }
                if (changed) {
                    byKey.put(document.repo() + "\0" + document.path(), copy(document, next, document.deleted()));
                }
            }
        }

        @Override
        public void markDeleted(String id) {
            for (CatalogDocument document : new ArrayList<>(byKey.values())) {
                if (document.id().equals(id)) {
                    byKey.put(document.repo() + "\0" + document.path(), copy(document, document.chunks(), true));
                }
            }
        }

        @Override
        public List<CatalogDocument> listActive(String repo) {
            List<CatalogDocument> found = new ArrayList<>();
            for (CatalogDocument document : byKey.values()) {
                if (repo.equals(document.repo()) && !document.deleted()) {
                    found.add(document);
                }
            }
            return found;
        }

        @Override
        public List<CatalogDocument> listVisible(boolean owner) {
            return visible(owner);
        }

        @Override
        public Optional<CatalogDocument> findVisible(String id, boolean owner) {
            for (CatalogDocument document : visible(owner)) {
                if (document.id().equals(id)) {
                    return Optional.of(document);
                }
            }
            return Optional.empty();
        }

        /** 测试里的可见文档直接当链接目标，锚点来自已经放进目录的小节。 */
        @Override
        public List<DocsLinkTarget> linkTargets(String repo, boolean owner) {
            List<DocsLinkTarget> found = new ArrayList<>();
            for (CatalogDocument document : visible(owner)) {
                if (!repo.equals(document.repo())) {
                    continue;
                }
                List<String> anchors = new ArrayList<>();
                for (CatalogChunk chunk : document.chunks()) {
                    if (chunk.anchor() != null && !chunk.anchor().isBlank()) {
                        anchors.add(chunk.anchor());
                    }
                }
                found.add(new DocsLinkTarget(document.id(), document.path(), List.copyOf(anchors)));
            }
            return found;
        }

        private List<CatalogDocument> visible(boolean owner) {
            List<CatalogDocument> found = new ArrayList<>();
            for (CatalogDocument document : byKey.values()) {
                if (document.deleted()) {
                    continue;
                }
                if ("public".equals(document.visibility()) || owner) {
                    found.add(document);
                }
            }
            return found;
        }

        private void put(CatalogDocument document) {
            byKey.put(document.repo() + "\0" + document.path(), document);
        }

        private int runs() {
            return runCount;
        }

        private static CatalogDocument copy(CatalogDocument document, List<CatalogChunk> chunks, boolean deleted) {
            return new CatalogDocument(
                    document.id(),
                    document.repo(),
                    document.path(),
                    document.title(),
                    document.topic(),
                    document.docType(),
                    document.visibility(),
                    document.source(),
                    document.contentHash(),
                    document.markdown(),
                    document.indexedAt(),
                    deleted,
                    List.copyOf(chunks));
        }
    }

    /** 只记写成功的 id。 */
    private static final class MemoryVectors implements DocsVectors {

        private final Set<String> ids = new HashSet<>();

        @Override
        public Set<String> present(Collection<String> ids) {
            Set<String> found = new HashSet<>();
            for (String id : ids) {
                if (this.ids.contains(id)) {
                    found.add(id);
                }
            }
            return found;
        }

        @Override
        public void upsert(String id, float[] vector, String docId, String topic, String visibility, String source) {
            ids.add(id);
        }

        @Override
        public void delete(Collection<String> ids) {
            this.ids.removeAll(ids);
        }

        /** 同步测试不检索。搜索有自己的假实现。 */
        @Override
        public List<com.zhu.docs.application.DocsVectorHit> search(float[] vector, String filter, int topK) {
            return List.of();
        }

        private Set<String> ids() {
            return ids;
        }
    }
}
