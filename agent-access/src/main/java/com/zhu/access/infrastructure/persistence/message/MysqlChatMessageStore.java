package com.zhu.access.infrastructure.persistence.message;

import com.zhu.common.util.UtcTimes;
import com.zhu.access.api.dto.chat.ChatMessageAttachmentItem;
import com.zhu.access.api.dto.chat.ChatMessageItem;
import com.zhu.access.application.chat.ChatAttachmentSupport;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.readmodel.entity.attachment.AttachmentMessageLink;
import com.zhu.readmodel.persistence.attachment.AttachmentMapper;
import com.zhu.access.infrastructure.persistence.entity.message.ChatMessage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把成功对话的气泡写入 MySQL。会话锁期间调用，按 max(seq) 递增。
 * 列表时按 MESSAGE 引用回填附件摘要。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlChatMessageStore implements ChatMessageStore {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final ChatMessageMapper messages;

    private final AttachmentMapper attachments;

    public MysqlChatMessageStore(ChatMessageMapper messages, AttachmentMapper attachments) {
        this.messages = messages;
        this.attachments = attachments;
    }

    /**
     * 先取最大序号，再插 user、assistant。空用户或空会话跳过并返回 0。
     */
    @Override
    public long appendTurn(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String userText,
            String assistantText) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return 0L;
        }
        String tenant = tenantId == null ? "" : tenantId;
        long next = messages.maxSeq(tenant, userId, sessionId) + 1L;
        LocalDateTime now = UtcTimes.now();
        messages.insertMessage(row(tenant, userId, sessionId, next, "user", userText, runId, now));
        messages.insertMessage(row(tenant, userId, sessionId, next + 1L, "assistant", assistantText, runId, now));
        return next;
    }

    /**
     * limit 夹到 1..200，默认 100。user 气泡带 MESSAGE 附件。
     */
    @Override
    public List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit) {
        int capped = limit < 1 ? 100 : Math.min(limit, 200);
        String tenant = tenantId == null ? "" : tenantId;
        List<ChatMessage> rows = messages.listOwned(tenant, userId, sessionId, capped);
        Map<String, List<ChatMessageAttachmentItem>> byRef = loadAttachments(tenant, userId, sessionId, rows);
        List<ChatMessageItem> out = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            if (row == null) {
                continue;
            }
            long seq = row.getSeq() == null ? 0L : row.getSeq();
            List<ChatMessageAttachmentItem> attached = List.of();
            if ("user".equalsIgnoreCase(row.getRole())) {
                attached = byRef.getOrDefault(ChatAttachmentSupport.messageRefId(sessionId, seq), List.of());
            }
            out.add(new ChatMessageItem(
                    seq,
                    row.getRole() == null ? "" : row.getRole(),
                    row.getContent() == null ? "" : row.getContent(),
                    row.getRunId(),
                    row.getCreatedAt() == null ? "" : UTC.format(row.getCreatedAt()),
                    attached));
        }
        return out;
    }

    private Map<String, List<ChatMessageAttachmentItem>> loadAttachments(
            String tenant, String userId, String sessionId, List<ChatMessage> rows) {
        List<String> refIds = new ArrayList<>();
        for (ChatMessage row : rows) {
            if (row == null || row.getSeq() == null || !"user".equalsIgnoreCase(row.getRole())) {
                continue;
            }
            refIds.add(ChatAttachmentSupport.messageRefId(sessionId, row.getSeq()));
        }
        if (refIds.isEmpty()) {
            return Map.of();
        }
        List<AttachmentMessageLink> links = attachments.listOwnedByMessageRefs(userId, tenant, refIds);
        Map<String, List<ChatMessageAttachmentItem>> byRef = new HashMap<>();
        for (AttachmentMessageLink link : links) {
            if (link == null || link.getRefId() == null || link.getAttachmentId() == null) {
                continue;
            }
            byRef.computeIfAbsent(link.getRefId(), key -> new ArrayList<>())
                    .add(new ChatMessageAttachmentItem(
                            link.getAttachmentId(),
                            link.getFileName() == null ? "" : link.getFileName(),
                            link.getMimeType() == null ? "" : link.getMimeType()));
        }
        return byRef;
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
