package com.zhu.audit.infrastructure.persistence.entity.billing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * model_price 的一行。改价是再插入一行，不覆盖旧行。
 * 单价单位是元/百万 token。
 * 字段名含数字时，驼峰转下划线会变成 input_per1m，必须显式写列名。
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

    /** 每百万输入 token 的单价。 */
    @TableField("input_per_1m")
    private BigDecimal inputPer1m;

    /** 每百万输出 token 的单价。 */
    @TableField("output_per_1m")
    private BigDecimal outputPer1m;

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

    /** 输入单价（元/百万 token）。 */
    public BigDecimal getInputPer1m() {
        return inputPer1m;
    }

    /** 写入输入单价。 */
    public void setInputPer1m(BigDecimal inputPer1m) {
        this.inputPer1m = inputPer1m;
    }

    /** 输出单价（元/百万 token）。 */
    public BigDecimal getOutputPer1m() {
        return outputPer1m;
    }

    /** 写入输出单价。 */
    public void setOutputPer1m(BigDecimal outputPer1m) {
        this.outputPer1m = outputPer1m;
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
