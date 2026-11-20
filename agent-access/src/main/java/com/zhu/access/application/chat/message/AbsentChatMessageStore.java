package com.zhu.access.application.chat.message;

import com.zhu.access.api.dto.chat.ChatMessageItem;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写消息历史。列表返回空，避免测试进程依赖表。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentChatMessageStore implements ChatMessageStore {

    @Override
    public void appendTurn(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String userText,
            String assistantText) {
    }

    @Override
    public List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit) {
        return List.of();
    }
}
