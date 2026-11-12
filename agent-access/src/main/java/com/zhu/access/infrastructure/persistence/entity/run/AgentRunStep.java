package com.zhu.access.infrastructure.persistence.entity.run;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * agent_run_step 的一行。不放参数、正文或异常原文。
 */
@TableName("agent_run_step")
public class AgentRunStep {

    /** 自增主键。同一次运行内用 stepNo 排序。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 从 1 开始。 */
    private Integer stepNo;

    /** REASONING、TOOL 或 APPROVAL。 */
    private String stepType;

    /** 工具名。推理步骤为空。 */
    private String toolId;

    /** SUCCEEDED、FAILED 或 SKIPPED。 */
    private String status;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 步骤序号。 */
    public Integer getStepNo() {
        return stepNo;
    }

    /** 写入步骤序号。 */
    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
    }

    /** 步骤类型。 */
    public String getStepType() {
        return stepType;
    }

    /** 写入步骤类型。 */
    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    /** 工具名。 */
    public String getToolId() {
        return toolId;
    }

    /** 写入工具名。 */
    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    /** 步骤状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入步骤状态。 */
    public void setStatus(String status) {
        this.status = status;
    }
}
