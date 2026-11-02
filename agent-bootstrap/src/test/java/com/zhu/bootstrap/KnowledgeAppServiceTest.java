package com.zhu.bootstrap;

import com.zhu.access.application.KnowledgeAppService;
import com.zhu.access.application.KnowledgeIndexLauncher;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.api.ObjectStore;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeDocumentItem;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传拒超大；文件进对象存储并挂 DOCUMENT；删除先清向量。不连库。
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
        when(repo.latestVersion("d1", "t", "u")).thenReturn(1);
        when(repo.listChunkIdsByDoc("d1", "t", "u")).thenReturn(List.of("c1", "c2"));
        when(repo.deleteDocument("d1", "t", "u")).thenReturn(1);
        KnowledgeAppService service = service(repo, vectors, mock(ObjectStore.class), null);
        service.deleteDocument("t", "u", "d1");
        verify(vectors).deleteById(eq("knowledge"), eq("c1"));
        verify(vectors).deleteById(eq("knowledge"), eq("c2"));
        verify(repo).deleteDocument("d1", "t", "u");
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

    private static KnowledgeAppService service(
            KnowledgeRepository repo, VectorStore vectors, ObjectStore objects, AttachmentCatalog catalog) {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRepository> repos = mock(ObjectProvider.class);
        when(repos.getIfAvailable()).thenReturn(repo);
        @SuppressWarnings("unchecked")
        ObjectProvider<AttachmentCatalog> attachments = mock(ObjectProvider.class);
        when(attachments.getIfAvailable()).thenReturn(catalog);
        return new KnowledgeAppService(
                repos, mock(KnowledgeIndexLauncher.class), vectors, objects, attachments, "companion-v1");
    }
}
