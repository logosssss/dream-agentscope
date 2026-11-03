package com.zhu.audit.api;

/**
 * 一条用户反馈详情。含创建时间，仍不带对话正文，也不表示运行仍在。
 *
 * @param id        表主键
 * @param runId     关联运行，可以空
 * @param rating    1 到 5
 * @param comment   短说明，可以空
 * @param createdAt 创建时间，UTC，没有时区后缀；没有时为空串
 */
public record FeedbackDetail(Long id, String runId, Integer rating, String comment, String createdAt) {
}
