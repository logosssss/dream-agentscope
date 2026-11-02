package com.zhu.audit.api;

/**
 * 一条已登记的用户反馈。不表示运行仍在，也不带对话正文。
 *
 * @param id      行主键
 * @param userId  提交人
 * @param runId   关联运行，可以空
 * @param rating  1 到 5
 * @param comment 短说明，可以空
 */
public record FeedbackSummary(Long id, String userId, String runId, Integer rating, String comment) {
}
