package com.zhu.docs.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 通用对话不能点名文档助手。空标识仍留给 main。
 */
class DocsAgentTest {

    /** 点名 docs-v1 与未知 Agent 一样，是不存在。 */
    @Test
    void publicChatCannotNameDocsAgent() {
        assertThatThrownBy(() -> DocsAgent.rejectPublicChat(DocsAgent.ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 空标识留给 main，其它产品标识不受这篇拒绝影响。 */
    @Test
    void otherAgentsStayOpen() {
        assertThatCode(() -> DocsAgent.rejectPublicChat(null)).doesNotThrowAnyException();
        assertThatCode(() -> DocsAgent.rejectPublicChat("companion-v1")).doesNotThrowAnyException();
    }
}
