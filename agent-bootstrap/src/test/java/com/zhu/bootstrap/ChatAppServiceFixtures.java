package com.zhu.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.access.api.attachment.AttachmentCatalog;
import com.zhu.access.application.attachment.AttachmentAppService;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.ChatAttachmentSupport;
import com.zhu.access.application.chat.message.AbsentChatMessageStore;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.runtime.api.agent.AgentExecutor;
import com.zhu.semantic.api.prompt.PromptService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 测试用 ChatAppService 装配。默认无附件目录，普通纯文本用例不受影响。
 */
final class ChatAppServiceFixtures {

    private ChatAppServiceFixtures() {
    }

    /** 缺省会话门禁放行、消息不落库。 */
    static ChatAppService chats(AgentExecutor executor) {
        return chats(executor, (tenantId, userId, sessionId) -> {
        }, new AbsentChatMessageStore());
    }

    /** 自定义会话归属门禁。 */
    static ChatAppService chats(AgentExecutor executor, SessionOwnerGate owners) {
        return chats(executor, owners, new AbsentChatMessageStore());
    }

    /** 自定义消息仓。 */
    static ChatAppService chats(AgentExecutor executor, ChatMessageStore messages) {
        return chats(executor, (tenantId, userId, sessionId) -> {
        }, messages);
    }

    /** 完整手工装配。 */
    static ChatAppService chats(AgentExecutor executor, SessionOwnerGate owners, ChatMessageStore messages) {
        PromptService prompts = (key, variables, fallback) -> fallback;
        return new ChatAppService(
                executor,
                prompts,
                new TestSemanticProvider(),
                new QuotaGuard(new FakeCacheFacade(), new AccessLimitProperties(), Clock.systemUTC()),
                new ConcurrentRunGuard(new FakeCacheFacade(), 120),
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                },
                owners,
                messages,
                emptyAttachmentSupport(),
                emptyCatalogs());
    }

    /** 无目录时的附件支持；有 attachmentIds 时会因目录不可用失败。 */
    static ChatAttachmentSupport emptyAttachmentSupport() {
        return new ChatAttachmentSupport(emptyCatalogs(), mock(AttachmentAppService.class));
    }

    @SuppressWarnings("unchecked")
    static ObjectProvider<AttachmentCatalog> emptyCatalogs() {
        ObjectProvider<AttachmentCatalog> catalogs = mock(ObjectProvider.class);
        when(catalogs.getIfAvailable()).thenReturn(null);
        return catalogs;
    }
}
