package com.zhu.bootstrap;

import com.zhu.audit.api.DailyCostAccumulator;
import com.zhu.audit.infrastructure.persistence.ModelPriceMapper;
import com.zhu.audit.infrastructure.persistence.MysqlTokenUsageWriter;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelPrice;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * token 用量表只记数字。没有当前价格时金额是 0。不连接 MySQL。
 */
class TokenUsageWriterTest {

    /** 负数 token 记成 0。没有价格时金额是 0。 */
    @Test
    void clampsTokensAndLeavesCostAtZero() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        new MysqlTokenUsageWriter(usages, prices, null).record("t", "u", " ", "run-1", "m", -2, 5);

        ArgumentCaptor<TokenUsage> saved = ArgumentCaptor.forClass(TokenUsage.class);
        verify(usages).insert(saved.capture());
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

    /** 1500 输入和 500 输出按每千 token 单价相加，并累加微单位。 */
    @Test
    void currentPriceSetsCostAndAccumulatesMicros() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        ModelPrice price = new ModelPrice();
        price.setCurrency("USD");
        price.setInputPer1k(new BigDecimal("0.002000"));
        price.setOutputPer1k(new BigDecimal("0.006000"));
        when(prices.selectCurrent(eq("m"), any(LocalDateTime.class))).thenReturn(price);
        AtomicLong micros = new AtomicLong();
        DailyCostAccumulator costs = (tenantId, userId, value) -> micros.set(value);

        new MysqlTokenUsageWriter(usages, prices, costs).record("t", "u", "s", "run-2", "m", 1500, 500);

        ArgumentCaptor<TokenUsage> saved = ArgumentCaptor.forClass(TokenUsage.class);
        verify(usages).insert(saved.capture());
        assertThat(saved.getValue().getCostAmount()).isEqualByComparingTo("0.006000");
        assertThat(saved.getValue().getCurrency()).isEqualTo("USD");
        assertThat(micros.get()).isEqualTo(6000L);
    }

    /** 没有用户时不插入，也不查价格。 */
    @Test
    void blankUserIsSkipped() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        ModelPriceMapper prices = mock(ModelPriceMapper.class);

        new MysqlTokenUsageWriter(usages, prices, null).record("t", " ", "s", "run-1", "m", 1, 1);

        verify(usages, never()).insert(any(TokenUsage.class));
        verify(prices, never()).selectCurrent(any(), any());
    }
}
