package com.zhu.access.application.chat;

import java.util.List;

/**
 * 一次对话结束后的运行记录。接入层用它留短摘要和步骤，不保存全文。
 */
public interface RunLog {

    /** 正常结束。 */
    String SUCCEEDED = "SUCCEEDED";

    /** 调用失败或流报错。 */
    String FAILED = "FAILED";

    /** 调用方取消了流。 */
    String CANCELLED = "CANCELLED";

    /**
     * 写入一行已经结束的运行。用户或会话为空时跳过。
     * 实现不得把异常原文或工具参数写入表。写失败由调用方吞掉，不改变回复。
     *
     * @param steps 这次看到的步骤。空列表表示没有步骤
     * @param startedNanos 调用开始时的 {@code System.nanoTime()}
     */
    void complete(
            String tenantId,
            String userId,
            String sessionId,
            String trigger,
            String status,
            String runId,
            String inputSummary,
            String outputSummary,
            String errorCode,
            List<RunStep> steps,
            long startedNanos);
}
