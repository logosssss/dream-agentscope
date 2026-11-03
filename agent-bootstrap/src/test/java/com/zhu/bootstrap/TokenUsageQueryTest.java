package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlTokenUsageQuery;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /** 记账时间为空时用空串。不改金额。 */
    @Test
    void ownedOneReturnsTokensAndCreatedAt() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        MysqlTokenUsageQuery query = new MysqlTokenUsageQuery(usages);
        TokenUsage row = new TokenUsage();
        row.setId(3L);
        row.setRunId("run-1");
        row.setTenantId("t");
        row.setSessionId("s");
        row.setModel("dashscope:qwen");
        row.setInputTokens(10L);
        row.setOutputTokens(4L);
        row.setTotalTokens(14L);
        row.setCostAmount(new BigDecimal("0.010000"));
        row.setCurrency("CNY");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 3));
        when(usages.findOwned(3L, "u", "t")).thenReturn(row);
        assertThat(query.ownedOne("u", "t", 3L)).satisfies(detail -> {
            assertThat(detail.id()).isEqualTo(3L);
            assertThat(detail.totalTokens()).isEqualTo(14L);
            assertThat(detail.costAmount()).isEqualByComparingTo("0.010000");
            assertThat(detail.createdAt()).isEqualTo("2026-11-02T01:02:03");
        });
        TokenUsage blank = new TokenUsage();
        blank.setId(4L);
        blank.setTenantId("");
        blank.setTotalTokens(1L);
        when(usages.findOwned(4L, "u", "")).thenReturn(blank);
        assertThat(query.ownedOne("u", " ", 4L).createdAt()).isEqualTo("");
    }

    /** 别人的或没有的按找不到。空用户或非法主键不查库。 */
    @Test
    void ownedOneRejectsMissingOrBlank() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        MysqlTokenUsageQuery query = new MysqlTokenUsageQuery(usages);
        when(usages.findOwned(9L, "u", "t")).thenReturn(null);
        assertThatThrownBy(() -> query.ownedOne("u", "t", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> query.ownedOne("u", "t", 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> query.ownedOne(" ", "t", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(usages).findOwned(9L, "u", "t");
    }
}
