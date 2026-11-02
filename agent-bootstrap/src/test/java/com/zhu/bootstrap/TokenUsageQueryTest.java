package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlTokenUsageQuery;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用量列表只读已经记下的行。不连接 MySQL，也不改金额。
 */
class TokenUsageQueryTest {

    /** 只列出本人本租户的行。空白租户按空串。条数 0 按默认 50。 */
    @Test
    void ownedListsRowsForTenant() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        MysqlTokenUsageQuery query = new MysqlTokenUsageQuery(usages);
        TokenUsage row = new TokenUsage();
        row.setId(3L);
        row.setRunId("run-1");
        row.setUserId("u");
        row.setTenantId("t");
        row.setSessionId("s");
        row.setModel("dashscope:qwen");
        row.setInputTokens(10L);
        row.setOutputTokens(4L);
        row.setTotalTokens(14L);
        row.setCostAmount(new BigDecimal("0.010000"));
        row.setCurrency("CNY");
        when(usages.listOwned("u", "t", 50)).thenReturn(List.of(row));
        assertThat(query.owned("u", "t", 0)).satisfies(list -> {
            assertThat(list).hasSize(1);
            assertThat(list.get(0).runId()).isEqualTo("run-1");
            assertThat(list.get(0).model()).isEqualTo("dashscope:qwen");
            assertThat(list.get(0).totalTokens()).isEqualTo(14L);
            assertThat(list.get(0).currency()).isEqualTo("CNY");
        });
        verify(usages).listOwned("u", "t", 50);
        assertThat(query.owned("u", " ", 200)).isEmpty();
        verify(usages).listOwned("u", "", 100);
    }

    /** 空用户按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankUser() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        MysqlTokenUsageQuery query = new MysqlTokenUsageQuery(usages);
        assertThatThrownBy(() -> query.owned(" ", "t", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(usages, never()).listOwned(any(), any(), anyInt());
    }
}
