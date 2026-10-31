package com.zhu.audit.infrastructure.persistence;

import java.math.BigDecimal;

/**
 * 一段用量的合计。只给账单汇总用，不是一张表。
 */
public class UsagePeriodTotal {

    /** token 合计。没有行时是 0。 */
    private Long tokenTotal;

    /** 金额合计。没有行时是 0。 */
    private BigDecimal costAmount;

    /** 区间里出现过的币种个数。 */
    private Integer currencyCount;

    /** 只有一种币种时的币种代码。 */
    private String currency;

    /** token 合计。 */
    public Long getTokenTotal() {
        return tokenTotal;
    }

    /** 写入 token 合计。 */
    public void setTokenTotal(Long tokenTotal) {
        this.tokenTotal = tokenTotal;
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
}
