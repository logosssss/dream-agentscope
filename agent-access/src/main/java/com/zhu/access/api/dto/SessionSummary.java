package com.zhu.access.api.dto;

/**
 * 会话列表里的一行。接入层返回给调用方，不包含对话正文。
 *
 * @param sessionId 会话标识
 * @param title 短标题。没有可用输入时为空串
 * @param channel WEB、IM 或 API
 * @param lastActiveAt 最近活动时间，UTC，没有时区后缀
 */
public record SessionSummary(String sessionId, String title, String channel, String lastActiveAt) {
}
