package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * model_price 的一行。改价是再插入一行，不覆盖旧行。
 */
@TableName("model_price")
public class ModelPrice {

    /** 自增主键。取价不看它。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型坐标。 */
    private String model;

    /** 币种。空时用量仍记 CNY。 */
    private String currency;

    /** 每千输入 token 的单价。 */
    private BigDecimal inputPer1k;

    /** 每千输出 token 的单价。 */
    private BigDecimal outputPer1k;

    /** 开始生效，含这一刻。 */
    private LocalDateTime effectiveFrom;

    /** 结束生效，不含这一刻。空表示仍有效。 */
    private LocalDateTime effectiveTo;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 模型坐标。 */
    public String getModel() {
        return model;
    }

    /** 写入模型坐标。 */
    public void setModel(String model) {
        this.model = model;
    }

    /** 币种。 */
    public String getCurrency() {
        return currency;
    }

    /** 写入币种。 */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /** 输入单价。 */
    public BigDecimal getInputPer1k() {
        return inputPer1k;
    }

    /** 写入输入单价。 */
    public void setInputPer1k(BigDecimal inputPer1k) {
        this.inputPer1k = inputPer1k;
    }

    /** 输出单价。 */
    public BigDecimal getOutputPer1k() {
        return outputPer1k;
    }

    /** 写入输出单价。 */
    public void setOutputPer1k(BigDecimal outputPer1k) {
        this.outputPer1k = outputPer1k;
    }

    /** 开始生效时间。 */
    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    /** 写入开始生效时间。 */
    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    /** 结束生效时间。 */
    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    /** 写入结束生效时间。空表示仍有效。 */
    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
