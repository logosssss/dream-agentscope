package com.zhu.access.application.chat;

import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.run.TurnRecorder;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.runtime.api.agent.AgentRequest;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.logging.Logger;

/**
 * 把一轮身份从 {@link AgentRequest} 填进会话索引和结束落库。接入层，WEB 与 API 共用。
 * 通道名、触发来源、标题正文和附件挂钩仍由调用方决定。
 */
final class SessionTurn {

    /** 只提供静态方法，避免两条入口各拆一遍租户、用户和会话。 */
    private SessionTurn() {
    }

    /**
     * 额度通过之后才记会话。记失败由调用方释放占位并让这次对话失败。
     */
    static void remember(SessionIndex sessions, AgentRequest request, String channel, String titleText) {
        sessions.record(request.tenantId(), request.userId(), request.sessionId(), channel, titleText);
    }

    /**
     * 结束时写运行记录和气泡。身份取已解析的请求，不回读请求体里的租户和用户。
     * 来源地址由调用方在请求线程上抄好再传入。附件挂钩只在需要时传入，不需要时传 null。
     */
    static void finish(
            Logger log,
            RunLog runs,
            SessionIndex sessions,
            ChatMessageStore messages,
            AgentRequest request,
            String trigger,
            String status,
            String runId,
            String requestId,
            String clientIp,
            String inputText,
            String output,
            String errorCode,
            List<RunStep> steps,
            long startedNanos,
            LongConsumer afterUserMessage) {
        TurnRecorder.complete(
                log,
                runs,
                sessions,
                messages,
                request.tenantId(),
                request.userId(),
                request.sessionId(),
                trigger,
                status,
                runId,
                requestId,
                clientIp,
                inputText,
                output,
                errorCode,
                steps,
                startedNanos,
                afterUserMessage);
    }
}
