package com.zhu.access.api.dto;

/**
 * 一次会话的详情。带最近运行和轮数，仍不含对话正文。
 *
 * @param sessionId    会话标识
 * @param title        短标题。没有可用输入时为空串
 * @param channel      WEB、IM 或 API
 * @param status       ACTIVE 或 ARCHIVED
 * @param lastRunId    最近一次运行，可以空
 * @param messageCount 已记录的对话轮数
 * @param lastActiveAt 最近活动时间，UTC，没有时区后缀
 */
public record SessionDetail(
        String sessionId,
        String title,
        String channel,
        String status,
        String lastRunId,
        int messageCount,
        String lastActiveAt) {
}
