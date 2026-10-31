package com.zhu.audit.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一行模型价格。改价不覆盖旧行，取价时用生效开始最晚且仍有效的一行。
 *
 * @param id           表主键。新插入后由库生成
 * @param model        模型坐标
 * @param currency     三位币种
 * @param inputPer1k   每千输入 token 单价
 * @param outputPer1k  每千输出 token 单价
 * @param effectiveFrom 开始生效，含这一刻
 * @param effectiveTo   结束生效，不含这一刻。空表示仍有效
 */
public record ModelPriceEntry(
        Long id,
        String model,
        String currency,
        BigDecimal inputPer1k,
        BigDecimal outputPer1k,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {
}
