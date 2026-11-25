package com.zhu.access.application.chat.run;

import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.access.application.chat.session.SessionListService;
import com.zhu.common.util.Ids;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.logging.Logger;

/**
 * 一轮结束后写运行记录和对话气泡。WEB 与 API 共用「写失败只打固定日志、不改回复」这一条。
 */
public final class TurnRecorder {

    /** 只提供静态方法，不给两条通道各留一份失败处理。 */
    private TurnRecorder() {
    }

    /**
     * 先写运行记录并挂到会话上。运行状态是成功时才追加气泡；写库失败不挡住气泡，也不抛给调用方。
     * 运行号为空时当场生成。摘要截断沿用列表标题。气泡正文不截断，空文本写成空串。
     *
     * @param afterUserMessage 气泡写入成功后的附加动作，参数是 user 消息序号。不需要时传 null
     */
    public static void complete(
            Logger log,
            RunLog runs,
            SessionIndex sessions,
            ChatMessageStore messages,
            String tenantId,
            String userId,
            String sessionId,
            String trigger,
            String status,
            String runId,
            String requestId,
            String inputText,
            String output,
            String errorCode,
            List<RunStep> steps,
            long startedNanos,
            LongConsumer afterUserMessage) {
        String id = runId == null || runId.isBlank() ? Ids.newId() : runId;
        try {
            runs.complete(
                    tenantId,
                    userId,
                    sessionId,
                    trigger,
                    status,
                    id,
                    requestId,
                    SessionListService.title(inputText),
                    SessionListService.title(output),
                    errorCode,
                    steps,
                    startedNanos);
            sessions.attachRun(tenantId, userId, sessionId, id);
        } catch (RuntimeException error) {
            log.warning("run record failed");
        }
        if (!RunLog.SUCCEEDED.equals(status)) {
            return;
        }
        try {
            long userSeq = messages.appendTurn(
                    tenantId,
                    userId,
                    sessionId,
                    id,
                    inputText == null ? "" : inputText,
                    output == null ? "" : output);
            if (afterUserMessage != null) {
                afterUserMessage.accept(userSeq);
            }
        } catch (RuntimeException error) {
            log.warning("chat message record failed");
        }
    }
}
