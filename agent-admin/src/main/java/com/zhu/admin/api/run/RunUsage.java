package com.zhu.admin.api.run;

import java.math.BigDecimal;

/**
 * 管理端看到的一轮用量。同一运行多行模型调用已加总。
 *
 * @param inputTokens  输入 token
 * @param outputTokens 输出 token
 * @param totalTokens  合计 token
 * @param costAmount   金额。没有价格表时是 0
 * @param currency     币种。混用多种币种时为空串
 * @param recorded     有用量行才是 true。全 0 且 false 表示这一轮没记用量
 */
public record RunUsage(
        long inputTokens,
        long outputTokens,
        long totalTokens,
        BigDecimal costAmount,
        String currency,
        boolean recorded) {

    /** 这一轮没有 token_usage 行。金额用 0，避免前端再判 null。 */
    public static RunUsage none() {
        return new RunUsage(0, 0, 0, BigDecimal.ZERO, "", false);
    }
}
