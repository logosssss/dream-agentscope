package com.zhu.docs.application;

import com.zhu.common.context.TenantContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.docs.api.DocsAsk;
import com.zhu.docs.api.DocsAskGate;
import com.zhu.docs.api.DocsAskPrompt;
import com.zhu.docs.api.DocsAskQuery;
import com.zhu.docs.api.DocsAskResult;
import com.zhu.docs.api.DocsCompleter;
import com.zhu.docs.api.DocsPage;
import com.zhu.docs.api.DocsQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 单篇问答。先确认这篇对当前用户可见，再扣额度并调用模型。
 * 看不见的文档在扣额度之前就返回，避免用一篇不存在的文档消耗次数。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class AskService implements DocsAsk {

    private final DocsQuery queries;

    private final DocsCompleter completer;

    private final DocsAskGate gate;

    /** 查询、补全和额度各自一个端口，单测可以不启动模型。 */
    public AskService(DocsQuery queries, DocsCompleter completer, DocsAskGate gate) {
        this.queries = queries;
        this.completer = completer;
        this.gate = gate;
    }

    /**
     * 未登录拒绝。问题空白拒绝。正文超过 6000 字时只带文首和命中的节。
     * 每次都是新的一轮，会话历史不从这里传入。
     */
    @Override
    public DocsAskResult ask(String userId, String docId, DocsAskQuery query) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login is required");
        }
        String question = query == null || query.q() == null ? "" : query.q().strip();
        if (question.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question is required");
        }
        DocsPage page = queries.page(userId, docId);
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return gate.enter(tenantId, userId, () -> complete(docId, page, question));
    }

    /** 系统提示用固定原文。文档放在用户消息里，不写进系统提示。 */
    private DocsAskResult complete(String docId, DocsPage page, String question) {
        String body = DocsExcerpt.fit(page.markdown(), question);
        String userText = DocsExcerpt.userMessage(page.title(), body, question);
        String answer = completer.complete(DocsAskPrompt.SYSTEM, userText);
        if (answer == null) {
            answer = "";
        }
        return new DocsAskResult(answer, DocsCitations.parse(docId, answer));
    }
}
