package com.zhu.access.infrastructure.persistence;

import com.zhu.access.api.dto.ChatMessageItem;
import com.zhu.access.application.ChatMessageStore;
import com.zhu.access.infrastructure.persistence.entity.ChatMessage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把成功对话的气泡写入 MySQL。会话锁期间调用，按 max(seq) 递增。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlChatMessageStore implements ChatMessageStore {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final ChatMessageMapper messages;

    public MysqlChatMessageStore(ChatMessageMapper messages) {
        this.messages = messages;
    }

    /**
     * 先取最大序号，再插 user、assistant。空用户或空会话跳过。
     */
    @Override
    public void appendTurn(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String userText,
            String assistantText) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String tenant = tenantId == null ? "" : tenantId;
        long next = messages.maxSeq(tenant, userId, sessionId) + 1L;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        messages.insertMessage(row(tenant, userId, sessionId, next, "user", userText, runId, now));
        messages.insertMessage(row(tenant, userId, sessionId, next + 1L, "assistant", assistantText, runId, now));
    }

    /**
     * limit 夹到 1..200，默认 100。
     */
    @Override
    public List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit) {
        int capped = limit < 1 ? 100 : Math.min(limit, 200);
        String tenant = tenantId == null ? "" : tenantId;
        List<ChatMessage> rows = messages.listOwned(tenant, userId, sessionId, capped);
        List<ChatMessageItem> out = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            if (row == null) {
                continue;
            }
            out.add(new ChatMessageItem(
                    row.getSeq() == null ? 0L : row.getSeq(),
                    row.getRole() == null ? "" : row.getRole(),
                    row.getContent() == null ? "" : row.getContent(),
                    row.getRunId(),
                    row.getCreatedAt() == null ? "" : UTC.format(row.getCreatedAt())));
        }
        return out;
    }

    private static ChatMessage row(
            String tenantId,
            String userId,
            String sessionId,
            long seq,
            String role,
            String content,
            String runId,
            LocalDateTime createdAt) {
        ChatMessage row = new ChatMessage();
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setSeq(seq);
        row.setRole(role);
        row.setContent(content == null ? "" : content);
        row.setRunId(runId == null || runId.isBlank() ? null : runId);
        row.setCreatedAt(createdAt);
        return row;
    }
}
