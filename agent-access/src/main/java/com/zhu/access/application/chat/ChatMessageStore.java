package com.zhu.access.application.chat;

import com.zhu.access.api.dto.ChatMessageItem;
import java.util.List;

/**
 * 会话消息读写缝。成功对话后双写；列表只给本人本租户。
 */
public interface ChatMessageStore {

    /**
     * 追加一轮 user + assistant。身份或会话为空时跳过。
     * 失败由实现吞掉或抛给调用方按软失败处理。
     */
    void appendTurn(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String userText,
            String assistantText);

    /**
     * 按 seq 升序列出气泡。limit 小于 1 按 100，大于 200 按 200。
     * 调用方须先确认会话归属。
     */
    List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit);
}
