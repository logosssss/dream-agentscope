package com.zhu.bootstrap;

import com.zhu.audit.infrastructure.persistence.MysqlTokenUsageWriter;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * token 用量表只记数字。没有价格时金额是 0。不连接 MySQL。
 */
class TokenUsageWriterTest {

    /** 负数 token 记成 0。合计是相加后的结果。 */
    @Test
    void clampsTokensAndLeavesCostAtZero() {
        TokenUsageMapper mapper = mock(TokenUsageMapper.class);
        new MysqlTokenUsageWriter(mapper).record("t", "u", " ", "run-1", "m", -2, 5);

        ArgumentCaptor<TokenUsage> saved = ArgumentCaptor.forClass(TokenUsage.class);
        verify(mapper).insert(saved.capture());
        TokenUsage row = saved.getValue();
        assertThat(row.getRunId()).isEqualTo("run-1");
        assertThat(row.getTenantId()).isEqualTo("t");
        assertThat(row.getSessionId()).isNull();
        assertThat(row.getInputTokens()).isZero();
        assertThat(row.getOutputTokens()).isEqualTo(5L);
        assertThat(row.getTotalTokens()).isEqualTo(5L);
        assertThat(row.getCostAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getCurrency()).isEqualTo("CNY");
    }

    /** 没有用户时不插入。 */
    @Test
    void blankUserIsSkipped() {
        TokenUsageMapper mapper = mock(TokenUsageMapper.class);

        new MysqlTokenUsageWriter(mapper).record("t", " ", "s", "run-1", "m", 1, 1);

        verify(mapper, never()).insert(any(TokenUsage.class));
    }
}
