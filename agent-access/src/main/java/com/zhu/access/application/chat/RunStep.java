package com.zhu.access.application.chat;

/**
 * 一次运行里的一步。只描述类型和工具名，不带参数或正文。
 *
 * @param stepType REASONING、TOOL 或 APPROVAL
 * @param toolId 工具名。推理步骤为空
 * @param status SUCCEEDED、FAILED 或 SKIPPED
 */
public record RunStep(String stepType, String toolId, String status) {

    /** 模型给出回复。 */
    public static final String REASONING = "REASONING";

    /** 开始调用工具。只表示看到了开始事件。 */
    public static final String TOOL = "TOOL";

    /** 发出了待确认。不表示用户已经决定。 */
    public static final String APPROVAL = "APPROVAL";

    /** 调用被取消，这一步没有结果。 */
    public static final String SKIPPED = "SKIPPED";
}
