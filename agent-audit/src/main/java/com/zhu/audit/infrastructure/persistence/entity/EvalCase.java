package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * eval_case 的一行。只保存输入和期望输出，不保存评分结果。
 */
@TableName("eval_case")
public class EvalCase {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据集标识。 */
    private String datasetId;

    /** 固定 1.0.0。 */
    private String version;

    /** 序号，从 1 起。 */
    private Integer caseNo;

    /** 输入。 */
    private String inputText;

    /** 期望输出。没有时留空。 */
    private String expectedText;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 数据集标识。 */
    public String getDatasetId() {
        return datasetId;
    }

    /** 写入数据集标识。 */
    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    /** 版本。 */
    public String getVersion() {
        return version;
    }

    /** 写入版本。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 序号。 */
    public Integer getCaseNo() {
        return caseNo;
    }

    /** 写入序号。 */
    public void setCaseNo(Integer caseNo) {
        this.caseNo = caseNo;
    }

    /** 输入。 */
    public String getInputText() {
        return inputText;
    }

    /** 写入输入。 */
    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    /** 期望输出。 */
    public String getExpectedText() {
        return expectedText;
    }

    /** 写入期望输出。 */
    public void setExpectedText(String expectedText) {
        this.expectedText = expectedText;
    }
}
