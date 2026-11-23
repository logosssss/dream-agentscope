package com.zhu.admin.api.run;

/**
 * 一次运行里的一个步骤。只读形状，不含工具参数与正文。
 *
 * @param stepNo   从 1 开始
 * @param stepType REASONING、TOOL 或 APPROVAL
 * @param toolId   工具名，推理步骤为空
 * @param status   SUCCEEDED、FAILED 或 SKIPPED
 */
public record RunStepRow(Integer stepNo, String stepType, String toolId, String status) {

    /** 从 readmodel 的行转过来，空值统一给空串，前端不用判 null。 */
    public static RunStepRow of(com.zhu.readmodel.entity.run.AgentRunStep row) {
        return new RunStepRow(
                row.getStepNo(),
                row.getStepType() == null ? "" : row.getStepType(),
                row.getToolId() == null ? "" : row.getToolId(),
                row.getStatus() == null ? "" : row.getStatus());
    }
}
