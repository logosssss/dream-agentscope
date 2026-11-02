package com.zhu.bootstrap;

import com.zhu.access.application.KnowledgeAppService;
import com.zhu.access.application.KnowledgeIndexLauncher;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传拒超大；删除先清向量。不连库。
 */
class KnowledgeAppServiceTest {

    @Test
    void rejectsOversizedContent() {
        KnowledgeAppService service = service(mock(KnowledgeRepository.class), mock(VectorStore.class));
        String huge = "汉".repeat(KnowledgeAppService.MAX_CONTENT_BYTES);
        assertThatThrownBy(() -> service.upload("t", "u", "kb", null, "t", huge))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void deleteDocumentDropsVectorsThenRows() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        VectorStore vectors = mock(VectorStore.class);
        when(repo.latestVersion("d1", "t", "u")).thenReturn(1);
        when(repo.listChunkIdsByDoc("d1", "t", "u")).thenReturn(List.of("c1", "c2"));
        when(repo.deleteDocument("d1", "t", "u")).thenReturn(1);
        KnowledgeAppService service = service(repo, vectors);
        service.deleteDocument("t", "u", "d1");
        verify(vectors).deleteById(eq("knowledge"), eq("c1"));
        verify(vectors).deleteById(eq("knowledge"), eq("c2"));
        verify(repo).deleteDocument("d1", "t", "u");
    }

    @Test
    void deleteMissingDocumentIsNotFound() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        when(repo.latestVersion(anyString(), anyString(), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service(repo, mock(VectorStore.class)).deleteDocument("t", "u", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void listBasesDelegates() {
        KnowledgeRepository repo = mock(KnowledgeRepository.class);
        when(repo.listBases("t", "u")).thenReturn(List.of());
        assertThat(service(repo, mock(VectorStore.class)).listBases("t", "u")).isEmpty();
    }

    private static KnowledgeAppService service(KnowledgeRepository repo, VectorStore vectors) {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRepository> repos = mock(ObjectProvider.class);
        when(repos.getIfAvailable()).thenReturn(repo);
        return new KnowledgeAppService(repos, mock(KnowledgeIndexLauncher.class), vectors, "companion-v1");
    }
}
