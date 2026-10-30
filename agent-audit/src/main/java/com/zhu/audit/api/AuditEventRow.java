package com.zhu.audit.api;

import java.time.LocalDateTime;

/**
 * 给管理进程看的一条审计。不带自增主键，也不带提示词全文。
 *
 * @param eventId   事件标识
 * @param eventType 动作名
 * @param userId    操作者，可能为空
 * @param sessionId 会话，可能为空
 * @param source    来源
 * @param payload   JSON 短说明
 * @param createdAt 入库时间，内存实现里为空
 */
public record AuditEventRow(
        String eventId,
        String eventType,
        String userId,
        String sessionId,
        String source,
        String payload,
        LocalDateTime createdAt
) {
}
