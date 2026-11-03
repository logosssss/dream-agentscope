package com.zhu.bootstrap;

import com.zhu.access.application.KnowledgeAppService;
import com.zhu.access.application.KnowledgeIndexLauncher;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.api.ObjectStore;
import com.zhu.access.api.dto.KnowledgeBaseDetail;
import com.zhu.access.api.dto.KnowledgeDocumentDetail;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeBaseOwned;
import com.zhu.state.api.KnowledgeDocumentItem;
import com.zhu.state.api.KnowledgeDocumentVersion;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorStore;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传拒超大；文件进对象存储并挂 DOCUMENT；删除先清向量和对象。不连库。
 */
class KnowledgeAppServiceTest {

    @Test
    void rejectsOversizedContent() {
        KnowledgeAppService service =
                service(mock(KnowledgeRepository.class), mock(VectorStore.class), mock(ObjectStore.class), null);
        String huge = "汉".repeat(KnowledgeAppService.MAX_CONTENT_BYTES);
        assertThatThrownBy(() -> service.upload("t", "u", "kb", null, "t", huge))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void uploadFileIndexesAndLinksDocumentAttachment() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(repo.findBase("kb1", "t", "u"))
                .thenReturn(new KnowledgeBaseItem("kb1", "t", "u", "agent-a", "n"));
        when(repo.insertDocumentVersion(
                        anyString(),
                        eq(1),
                        eq("kb1"),
                        eq("t"),
                        eq("u"),
                        eq("agent-a"),
                        eq("readme"),
                        eq("hello"),
                        eq(false)))
                .thenReturn(new KnowledgeDocumentItem(
                        "doc-9", 1, "kb1", "t", "u", "agent-a", "readme", null, "PENDING", null));
        String objectKey = "kb/t/u/doc-9/v1/readme.md";
        when(objects.put(eq(objectKey), any())).thenReturn(objectKey);
        when(objects.storageType()).thenReturn("LOCAL");
        when(catalog.register(
                        eq("u"),
                        eq("t"),
                        isNull(),
                        eq("readme.md"),
                        eq("LOCAL"),
                        eq(objectKey),
                        eq("text/markdown"),
                        anyLong(),
                        isNull()))
                .thenReturn(new AttachmentSummary("att-1", "t", "u", null, "readme.md", "LOCAL", objectKey, "ACTIVE"));

        KnowledgeAppService service = service(repo, mock(VectorStore.class), objects, catalog);
        KnowledgeDocumentItem saved = service.uploadFile(
                "t",
                "u",
                "kb1",
                null,
                null,
                "readme.md",
                "text/markdown",
                "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.docId()).isEqualTo("doc-9");
        verify(objects).put(eq(objectKey), any());
        verify(catalog).link("u", "att-1", "DOCUMENT", "doc-9");
    }

    @Test
    void deleteDocumentDropsVectorsThenRows() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        VectorStore vectors = mock(VectorStore.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(repo.latestVersion("d1", "t", "u")).thenReturn(1);
        when(repo.listChunkIdsByDoc("d1", "t", "u")).thenReturn(List.of("c1", "c2"));
        when(repo.deleteDocument("d1", "t", "u")).thenReturn(1);
        KnowledgeAppService service = service(repo, vectors, objects, null);
        service.deleteDocument("t", "u", "d1");
        verify(vectors).deleteById(eq("knowledge"), eq("c1"));
        verify(vectors).deleteById(eq("knowledge"), eq("c2"));
        verify(objects, never()).delete(anyString());
        verify(repo).deleteDocument("d1", "t", "u");
    }

    /** 按 DOCUMENT 引用清对象。单个对象失败不挡删行。 */
    @Test
    void deleteDocumentDropsLinkedObjects() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(repo.latestVersion("d1", "t", "u")).thenReturn(1);
        when(repo.listChunkIdsByDoc("d1", "t", "u")).thenReturn(List.of());
        when(repo.deleteDocument("d1", "t", "u")).thenReturn(1);
        when(catalog.storagePaths("u", "t", "DOCUMENT", "d1"))
                .thenReturn(List.of("kb/t/u/d1/v1/a.txt", "kb/t/u/d1/v2/a.txt"));
        doThrow(new IllegalStateException("oss")).when(objects).delete("kb/t/u/d1/v1/a.txt");
        service(repo, mock(VectorStore.class), objects, catalog).deleteDocument("t", "u", "d1");
        verify(objects).delete("kb/t/u/d1/v1/a.txt");
        verify(objects).delete("kb/t/u/d1/v2/a.txt");
        verify(repo).deleteDocument("d1", "t", "u");
    }

    /** 删库时按库内文档逐个清对象。 */
    @Test
    void deleteBaseDropsObjectsForEachDocument() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(repo.findBase("kb1", "t", "u"))
                .thenReturn(new KnowledgeBaseItem("kb1", "t", "u", "agent-a", "n"));
        when(repo.listChunkIdsByBase("kb1", "t", "u")).thenReturn(List.of());
        when(repo.listLatestDocuments("kb1", "t", "u"))
                .thenReturn(List.of(new KnowledgeDocumentItem(
                        "d1", 1, "kb1", "t", "u", "agent-a", "n", null, "SUCCESS", null)));
        when(repo.deleteBase("kb1", "t", "u")).thenReturn(1);
        when(catalog.storagePaths("u", "t", "DOCUMENT", "d1")).thenReturn(List.of("kb/t/u/d1/v1/a.txt"));
        service(repo, mock(VectorStore.class), objects, catalog).deleteBase("t", "u", "kb1");
        verify(objects).delete("kb/t/u/d1/v1/a.txt");
        verify(repo).deleteBase("kb1", "t", "u");
    }

    @Test
    void deleteMissingDocumentIsNotFound() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        when(repo.latestVersion(anyString(), anyString(), anyString())).thenReturn(0);
        assertThatThrownBy(() ->
                        service(repo, mock(VectorStore.class), mock(ObjectStore.class), null)
                                .deleteDocument("t", "u", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void listBasesDelegates() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        when(repo.listBases("t", "u")).thenReturn(List.of());
        assertThat(service(repo, mock(VectorStore.class), mock(ObjectStore.class), null).listBases("t", "u"))
                .isEmpty();
    }

    /** 先确认归属再返回库名和创建时间。不读文档。 */
    @Test
    void getBaseReturnsOwnedWithCreatedAt() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        when(repo.findOwnedBase("kb1", "t", "u")).thenReturn(new KnowledgeBaseOwned(
                "kb1", "t", "u", "companion-v1", "示例", LocalDateTime.of(2026, 11, 2, 1, 2, 3)));
        KnowledgeBaseDetail detail =
                service(repo, mock(VectorStore.class), mock(ObjectStore.class), null).getBase("t", "u", "kb1");
        assertThat(detail).isEqualTo(new KnowledgeBaseDetail(
                "kb1", "t", "u", "companion-v1", "示例", "2026-11-02T01:02:03"));
        verify(repo, never()).listLatestDocuments(any(), any(), any());
    }

    /** 空标识不查库。别人的库按找不到。 */
    @Test
    void getBaseRejectsMissingOrBlank() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        KnowledgeAppService knowledge = service(repo, mock(VectorStore.class), mock(ObjectStore.class), null);
        assertThatThrownBy(() -> knowledge.getBase("t", "u", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(repo, never()).findOwnedBase(eq(" "), any(), any());
        when(repo.findOwnedBase("missing", "t", "u")).thenReturn(null);
        assertThatThrownBy(() -> knowledge.getBase("t", "u", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(repo).findOwnedBase("missing", "t", "u");
    }

    /** 最新一版带创建和更新时间。不读正文，也不触发索引。 */
    @Test
    void documentReturnsLatestTimesWithoutContent() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        KnowledgeIndexLauncher indexer = mock(KnowledgeIndexLauncher.class);
        when(repo.findLatestDocument("doc-1", "t", "u")).thenReturn(new KnowledgeDocumentVersion(
                "doc-1", 2, "kb1", "agent-a", "readme", "SUCCESS", null,
                LocalDateTime.of(2026, 11, 2, 1, 2, 3),
                LocalDateTime.of(2026, 11, 2, 4, 5, 6)));
        KnowledgeAppService knowledge = service(repo, mock(VectorStore.class), mock(ObjectStore.class), null, indexer);

        KnowledgeDocumentDetail detail = knowledge.document("t", "u", "doc-1");

        assertThat(detail).isEqualTo(new KnowledgeDocumentDetail(
                "doc-1", 2, "kb1", "agent-a", "readme", "SUCCESS", null,
                "2026-11-02T01:02:03", "2026-11-02T04:05:06"));
        verify(indexer, never()).enqueue(any(), any(), any(), any(), anyInt());
    }

    /** 别人的按找不到。空标识不查库。 */
    @Test
    void documentRejectsMissingOrBlank() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        when(repo.findLatestDocument("missing", "t", "u")).thenReturn(null);
        KnowledgeAppService knowledge = service(repo, mock(VectorStore.class), mock(ObjectStore.class), null);

        assertThatThrownBy(() -> knowledge.document("t", "u", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> knowledge.document("t", "u", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(repo, never()).findLatestDocument(eq(" "), any(), any());
    }

    private static KnowledgeAppService service(
            KnowledgeRepository repo, VectorStore vectors, ObjectStore objects, AttachmentCatalog catalog) {
        return service(repo, vectors, objects, catalog, mock(KnowledgeIndexLauncher.class));
    }

    private static KnowledgeAppService service(
            KnowledgeRepository repo,
            VectorStore vectors,
            ObjectStore objects,
            AttachmentCatalog catalog,
            KnowledgeIndexLauncher indexer) {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRepository> repos = mock(ObjectProvider.class);
        when(repos.getIfAvailable()).thenReturn(repo);
        @SuppressWarnings("unchecked")
        ObjectProvider<AttachmentCatalog> attachments = mock(ObjectProvider.class);
        when(attachments.getIfAvailable()).thenReturn(catalog);
        return new KnowledgeAppService(
                repos, indexer, vectors, objects, attachments, "companion-v1");
    }
}
