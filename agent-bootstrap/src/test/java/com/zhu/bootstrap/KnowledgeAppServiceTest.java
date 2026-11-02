package com.zhu.bootstrap;

import com.zhu.access.application.KnowledgeAppService;
import com.zhu.access.application.KnowledgeIndexLauncher;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.KnowledgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 上传入口拒收超大正文。不连库。
 */
class KnowledgeAppServiceTest {

    @Test
    void rejectsOversizedContent() {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRepository> repos = mock(ObjectProvider.class);
        when(repos.getIfAvailable()).thenReturn(mock(KnowledgeRepository.class));
        KnowledgeAppService service =
                new KnowledgeAppService(repos, mock(KnowledgeIndexLauncher.class), "companion-v1");
        String huge = "汉".repeat(KnowledgeAppService.MAX_CONTENT_BYTES);
        assertThatThrownBy(() -> service.upload("t", "u", "kb", null, "t", huge))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
