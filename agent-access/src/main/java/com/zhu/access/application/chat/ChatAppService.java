package com.zhu.access.application.chat;

import com.zhu.ops.attachment.AttachmentCatalog;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.access.infrastructure.security.ClientIp;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 把接入层请求译成 AgentRequest，再交给 Channel Gateway（同 session 由框架排队）。
 */
@Service
public class ChatAppService {

    private static final Logger log = Logger.getLogger(ChatAppService.class.getName());

    private final AgentExecutor executor;

    private final PromptService prompts;

    private final ProductSemanticProvider semantics;

    private final QuotaGuard quota;

    private final ConcurrentRunGuard concurrent;

    private final SessionIndex sessions;

    private final RunLog runs;

    private final TenantGate tenants;

    private final TenantUserGate tenantUsers;

    private final SessionOwnerGate owners;

    private final ChatMessageStore messages;

    private final ChatAttachmentSupport attachments;

    private final ObjectProvider<AttachmentCatalog> catalogs;

    /**
     * 租户、租户用户和会话归属在额度和租户并发占位之前。MySQL 关闭时门禁放行。
     * 同会话串行交给 Gateway，不再用 SessionTurnGuard 忙则拒。
     */
    public ChatAppService(
            AgentExecutor executor,
            PromptService prompts,
            ProductSemanticProvider semantics,
            QuotaGuard quota,
            ConcurrentRunGuard concurrent,
            SessionIndex sessions,
            RunLog runs,
            TenantGate tenants,
            TenantUserGate tenantUsers,
            SessionOwnerGate owners,
            ChatMessageStore messages,
            ChatAttachmentSupport attachments,
            ObjectProvider<AttachmentCatalog> catalogs) {
        this.executor = executor;
        this.prompts = prompts;
        this.semantics = semantics;
        this.quota = quota;
        this.concurrent = concurrent;
        this.sessions = sessions;
        this.runs = runs;
        this.tenants = tenants;
        this.tenantUsers = tenantUsers;
        this.owners = owners;
        this.messages = messages;
        this.attachments = attachments;
        this.catalogs = catalogs;
    }

    /**
     * 同步执行一次对话（WEB / 默认通道）。
     * 租户和用户只取登录会话写进线程的值。请求体里的身份丢掉。通道留在接入层。
     * 显式 {@code channel=API} 须改走 {@code /api/agent/chat*}。
     */
    public AgentResponse chat(ChatRequest request) {
        rejectExplicitApiChannel(request);
        String clientIp = ClientIp.capture();
        PreparedTurn prepared = prepare(request);
        AgentRequest agentRequest = prepared.agentRequest();
        return GatedChat.sync(
                tenants,
                tenantUsers,
                owners,
                quota,
                concurrent,
                agentRequest,
                () -> remember(request, prepared),
                () -> executor.execute(agentRequest),
                (status, runId, output, errorCode, requestId, started, steps) -> finish(
                        request, prepared, status, runId, output, errorCode, requestId, clientIp, started, steps));
    }

    /**
     * 流式执行（WEB / 默认通道）。身份和来源地址在返回 Flux 之前就抄走，避免过滤器清掉线程上下文后流才开始读。
     * 会话锁和并发占位在流结束时释放。调用方不要 block。
     * 显式 {@code channel=API} 须改走 {@code /api/agent/chat/stream}。
     */
    public Flux<AgentStreamEvent> stream(ChatRequest request) {
        rejectExplicitApiChannel(request);
        String clientIp = ClientIp.capture();
        PreparedTurn prepared = prepare(request);
        AgentRequest agentRequest = prepared.agentRequest();
        return GatedChat.stream(
                tenants,
                tenantUsers,
                owners,
                quota,
                concurrent,
                agentRequest,
                () -> remember(request, prepared),
                () -> executor.stream(agentRequest),
                (status, runId, output, errorCode, requestId, started, steps) -> finish(
                        request, prepared, status, runId, output, errorCode, requestId, clientIp, started, steps));
    }

    /**
     * 额度通过、占会话锁和并发名额之后才记会话。记失败会释放并让这次对话失败。
     * 通道留在列表里，不放进 AgentRequest。标题用解析后的用户正文。
     */
    private void remember(ChatRequest request, PreparedTurn prepared) {
        SessionTurn.remember(
                sessions,
                prepared.agentRequest(),
                request.channelOrDefault().name(),
                prepared.displayText());
    }

    /**
     * 运行记录写失败只打固定日志，不改变回复，也不把异常原文打出来。
     * 成功时再双写 chat_message，并把本轮附件挂到 MESSAGE 引用。
     * 摘要沿用列表标题的长度，换行压成空格。步骤只带类型和工具名。
     */
    private void finish(
            ChatRequest request,
            PreparedTurn prepared,
            String status,
            String runId,
            String output,
            String errorCode,
            String requestId,
            String clientIp,
            long startedNanos,
            List<RunStep> steps) {
        AgentRequest agentRequest = prepared.agentRequest();
        SessionTurn.finish(
                log,
                runs,
                sessions,
                messages,
                agentRequest,
                trigger(request),
                status,
                runId,
                requestId,
                clientIp,
                prepared.displayText(),
                output,
                errorCode,
                steps,
                startedNanos,
                userSeq -> linkAttachments(
                        agentRequest.userId(), agentRequest.sessionId(), userSeq, prepared.attachmentIds()));
    }

    /** 成功落库后把附件挂到 user 气泡；目录不可用或 seq 无效时跳过。 */
    private void linkAttachments(String userId, String sessionId, long userSeq, List<String> attachmentIds) {
        if (userSeq < 1 || attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        AttachmentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            return;
        }
        String refId = ChatAttachmentSupport.messageRefId(sessionId, userSeq);
        for (String attachmentId : attachmentIds) {
            try {
                catalog.link(userId, attachmentId, "MESSAGE", refId);
            } catch (RuntimeException error) {
                log.warning("attachment message link failed");
            }
        }
    }

    /** 程序调用记成 API。页面和即时通讯仍是用户触发。 */
    private static String trigger(ChatRequest request) {
        if (request.channelOrDefault() == AccessChannel.API) {
            return "API";
        }
        return "USER";
    }

    /**
     * 通道只在接入层归一化，不传给运行层。
     * WEB 通道恒为 main：请求里的 agentId 不写进 AgentRequest，避免页面靠一个字段切产品。
     * 附件仍按调用方给的 agentId 校验，非搭子标识不能带附件。
     * 校验通过后补默认句，并把本轮图片装进 AgentRequest。
     */
    private PreparedTurn prepare(ChatRequest request) {
        request.channelOrDefault();
        String tenantId = AccessCaller.tenantId();
        String userId = UserContext.get();
        ChatAttachmentSupport.Resolved resolved =
                attachments.resolve(request.agentId(), userId, request.attachmentIdsOrEmpty());
        String displayText = ChatAttachmentSupport.resolveUserText(request.text(), !resolved.isEmpty());
        if (displayText.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId, sessionId and userText are required");
        }
        AgentRequest agentRequest = new AgentRequest(
                null,
                tenantId,
                userId,
                request.sessionId(),
                AccessCaller.systemPrompt(prompts, semantics),
                displayText,
                resolved.images());
        return new PreparedTurn(agentRequest, resolved.attachmentIds(), displayText);
    }

    /**
     * 本轮解析结果：进 Gateway 的请求、待挂 MESSAGE 的附件 id、落库用正文。
     */
    private record PreparedTurn(AgentRequest agentRequest, List<String> attachmentIds, String displayText) {
        private PreparedTurn {
            attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
            displayText = displayText == null ? "" : displayText;
        }
    }

    /**
     * 旧 {@code /api/chat*} 显式 API 通道拒绝，避免与 ApiChannel 入口双歧。
     */
    private static void rejectExplicitApiChannel(ChatRequest request) {
        if (request != null && request.channelOrDefault() == AccessChannel.API) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "use /api/agent/chat or /api/agent/chat/stream for channel=API");
        }
    }

}
