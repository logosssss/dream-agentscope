package com.zhu.admin.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 控制台新增的一行模型价格。不覆盖旧行。
 * 单价单位是元/百万 token。
 *
 * @param model         模型坐标
 * @param currency      三位币种。空则记 CNY
 * @param inputPer1m    每百万输入 token 单价
 * @param outputPer1m   每百万输出 token 单价
 * @param effectiveFrom 开始生效。空则用 UTC 现在
 * @param effectiveTo   结束生效。空表示仍有效
 */
public record ModelPriceRequest(
        String model,
        String currency,
        BigDecimal inputPer1m,
        BigDecimal outputPer1m,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {
}
