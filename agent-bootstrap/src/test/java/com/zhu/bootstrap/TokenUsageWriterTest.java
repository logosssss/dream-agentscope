package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.persistence.usage.MysqlTokenUsageWriter;
import com.zhu.runtime.api.usage.DailyCostAccumulator;
import com.zhu.readmodel.persistence.billing.ModelPriceMapper;
import com.zhu.runtime.api.usage.TokenUsageWriter;
import com.zhu.readmodel.persistence.usage.TokenUsageMapper;
import com.zhu.readmodel.entity.billing.ModelPrice;
import com.zhu.readmodel.entity.usage.TokenUsage;
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

    /**
     * 有价目行时，金额指标跟着落库金额走（同一口径的微单位）。
     * 这条盯的是「指标真的接在写入处」，不是只测 {@code TurnMetrics} 自己的方法。
     */
    @Test
    void recordsCostMetricAlongWithTheRow() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        com.zhu.runtime.application.metrics.TurnMetrics metrics =
                new com.zhu.runtime.application.metrics.TurnMetrics(new org.springframework.beans.factory.ObjectProvider<>() {

                    @Override
                    public io.micrometer.core.instrument.MeterRegistry getObject() {
                        return registry;
                    }

                    @Override
                    public io.micrometer.core.instrument.MeterRegistry getIfAvailable() {
                        return registry;
                    }
                });

        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        ModelPrice price = new ModelPrice();
        price.setCurrency("CNY");
        price.setInputPer1m(new BigDecimal("2.000000"));
        price.setOutputPer1m(new BigDecimal("8.000000"));
        when(prices.selectCurrent(eq("m"), any(LocalDateTime.class))).thenReturn(price);

        new MysqlTokenUsageWriter(usages, prices, null, metrics)
                .record("t", "u", "s", "run-1", "m", 1_000_000, 500_000);

        // 1M 输入 × 2 元 + 0.5M 输出 × 8 元 = 6 元 = 6,000,000 微单位
        assertThat(registry.get("agentscope.model.cost").tag("model", "m").counter().count())
                .isEqualTo(6_000_000.0);
    }

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

    /**
     * 1500 输入和 500 输出按每百万 token 单价相加，并累加微单位。
     * 单价 2 / 6 元每百万，与旧「每千 0.002 / 0.006」等价，金额仍是 0.006。
     */
    @Test
    void currentPriceSetsCostAndAccumulatesMicros() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        ModelPrice price = new ModelPrice();
        price.setCurrency("USD");
        price.setInputPer1m(new BigDecimal("2.000000"));
        price.setOutputPer1m(new BigDecimal("6.000000"));
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

    /** 查价抛错时仍写入 token 行，金额记 0，不挡对话后记账。 */
    @Test
    void priceLookupFailureStillInsertsWithZeroCost() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        when(prices.selectCurrent(eq("m"), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Unknown column 'input_per_1m'"));

        new MysqlTokenUsageWriter(usages, prices, null).record("t", "u", "s", "run-3", "m", 10, 2);

        ArgumentCaptor<TokenUsage> saved = ArgumentCaptor.forClass(TokenUsage.class);
        verify(usages).insert(saved.capture());
        assertThat(saved.getValue().getTotalTokens()).isEqualTo(12L);
        assertThat(saved.getValue().getCostAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getValue().getCurrency()).isEqualTo("CNY");
    }
}
