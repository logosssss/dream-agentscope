package com.zhu.audit.domain;

import com.zhu.audit.infrastructure.persistence.entity.ModelPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 按每千 token 单价算金额。与 token_usage.cost_amount 同公式。
 */
public final class ModelCosting {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /** 金额小数位，与落库一致。 */
    public static final int SCALE = 6;

    private ModelCosting() {
    }

    /**
     * 金额。没有价格或单价为空时为 0。
     */
    public static BigDecimal amount(long inputTokens, long outputTokens, ModelPrice price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputCost = rate(price.getInputPer1k()).multiply(BigDecimal.valueOf(Math.max(0L, inputTokens)));
        BigDecimal outputCost = rate(price.getOutputPer1k()).multiply(BigDecimal.valueOf(Math.max(0L, outputTokens)));
        return inputCost.add(outputCost).divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 金额微单位，供 Redis 整数累加。与 limit_value（COST）同一尺度。
     */
    public static long micros(long inputTokens, long outputTokens, ModelPrice price) {
        BigDecimal value = amount(inputTokens, outputTokens, price);
        if (value.signum() <= 0) {
            return 0L;
        }
        try {
            return value.movePointRight(SCALE).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static BigDecimal rate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
