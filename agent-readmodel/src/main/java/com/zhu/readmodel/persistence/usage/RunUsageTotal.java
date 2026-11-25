package com.zhu.readmodel.persistence.usage;

import java.math.BigDecimal;

/**
 * 一轮运行的用量合计。同一 run_id 可能有多行模型调用，这里加总，不是一张表。
 */
public class RunUsageTotal {

    /** 输入 token 合计。没有行时是 0。 */
    private Long inputTokens;

    /** 输出 token 合计。没有行时是 0。 */
    private Long outputTokens;

    /** 输入加输出。没有行时是 0。 */
    private Long totalTokens;

    /** 金额合计。没有价格时各行是 0，加总仍是 0。 */
    private BigDecimal costAmount;

    /** 这一轮出现过的币种个数。多于 1 时不能当成同一种钱。 */
    private Integer currencyCount;

    /** 只有一种币种时的代码。 */
    private String currency;

    /** 命中的用量行数。0 表示这一轮还没记用量。 */
    private Long rowCount;

    /** 输入 token 合计。 */
    public Long getInputTokens() {
        return inputTokens;
    }

    /** 写入输入 token 合计。 */
    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    /** 输出 token 合计。 */
    public Long getOutputTokens() {
        return outputTokens;
    }

    /** 写入输出 token 合计。 */
    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }

    /** token 合计。 */
    public Long getTotalTokens() {
        return totalTokens;
    }

    /** 写入 token 合计。 */
    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    /** 金额合计。 */
    public BigDecimal getCostAmount() {
        return costAmount;
    }

    /** 写入金额合计。 */
    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    /** 币种个数。 */
    public Integer getCurrencyCount() {
        return currencyCount;
    }

    /** 写入币种个数。 */
    public void setCurrencyCount(Integer currencyCount) {
        this.currencyCount = currencyCount;
    }

    /** 币种。 */
    public String getCurrency() {
        return currency;
    }

    /** 写入币种。 */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /** 用量行数。 */
    public Long getRowCount() {
        return rowCount;
    }

    /** 写入用量行数。 */
    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }
}
