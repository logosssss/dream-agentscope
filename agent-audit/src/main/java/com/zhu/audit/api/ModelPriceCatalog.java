package com.zhu.audit.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型价格的查询和新增。不修改已经写下的旧行。
 * 单价单位是元/百万 token。
 */
public interface ModelPriceCatalog {

    /**
     * 按生效开始时间从新到旧列出价格。条数默认 50，最多 100。
     */
    List<ModelPriceEntry> recent(int limit);

    /**
     * 再插入一行价格。生效开始为空时用 UTC 现在，和取价时钟一致。
     * 结束时间不晚于开始时间时拒绝。单价小于 0 时拒绝。
     */
    ModelPriceEntry add(
            String model,
            String currency,
            BigDecimal inputPer1m,
            BigDecimal outputPer1m,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo);
}
