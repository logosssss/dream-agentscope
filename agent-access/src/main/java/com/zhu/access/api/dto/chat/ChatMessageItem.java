package com.zhu.access.api.dto.chat;

import java.util.List;

/**
 * 会话里的一条气泡。按 seq 升序展示。
 *
 * @param seq         会话内序号
 * @param role        user 或 assistant
 * @param content     正文，可空串
 * @param runId       同轮运行标识，可空
 * @param createdAt   UTC 时间，没有时区后缀
 * @param attachments 本条挂上的附件；助手气泡通常为空
 */
public record ChatMessageItem(
        long seq,
        String role,
        String content,
        String runId,
        String createdAt,
        List<ChatMessageAttachmentItem> attachments
) {

    /**
     * 无附件时的兼容构造。
     */
    public ChatMessageItem(long seq, String role, String content, String runId, String createdAt) {
        this(seq, role, content, runId, createdAt, List.of());
    }

    /**
     * 规范化附件列表。
     */
    public ChatMessageItem {
        attachments = attachments == null || attachments.isEmpty() ? List.of() : List.copyOf(attachments);
    }
}
