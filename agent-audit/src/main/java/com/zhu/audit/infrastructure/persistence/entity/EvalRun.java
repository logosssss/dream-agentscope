package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * eval_run 的一行。打分只改通过数、通过率和状态，不写逐条结果。
 */
@TableName("eval_run")
public class EvalRun {

    /** 自增主键。对外用 evalRunId。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 这次执行的标识。 */
    private String evalRunId;

    /** 数据集标识。 */
    private String datasetId;

    /** 固定 1.0.0。 */
    private String datasetVersion;

    /** 开始时的用例条数。 */
    private Integer totalCases;

    /** 通过条数。登记时是 0，打分后写入。 */
    private Integer passedCases;

    /** RUNNING 或 SUCCEEDED。 */
    private String status;

    /** 登记时间。 */
    private LocalDateTime startedAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 执行标识。 */
    public String getEvalRunId() {
        return evalRunId;
    }

    /** 写入执行标识。 */
    public void setEvalRunId(String evalRunId) {
        this.evalRunId = evalRunId;
    }

    /** 数据集标识。 */
    public String getDatasetId() {
        return datasetId;
    }

    /** 写入数据集标识。 */
    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    /** 数据集版本。 */
    public String getDatasetVersion() {
        return datasetVersion;
    }

    /** 写入数据集版本。 */
    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    /** 用例条数。 */
    public Integer getTotalCases() {
        return totalCases;
    }

    /** 写入用例条数。 */
    public void setTotalCases(Integer totalCases) {
        this.totalCases = totalCases;
    }

    /** 通过条数。 */
    public Integer getPassedCases() {
        return passedCases;
    }

    /** 写入通过条数。 */
    public void setPassedCases(Integer passedCases) {
        this.passedCases = passedCases;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 开始时间。 */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /** 写入开始时间。 */
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
}
