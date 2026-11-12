package com.zhu.access.api.dto.account;

/**
 * 当前用户提交的反馈。评分由服务端限制在 1 到 5，不从这里接受标签。
 *
 * @param runId   运行标识，可以空
 * @param rating  1 到 5
 * @param comment 短说明，可以空
 */
public record FeedbackRequest(String runId, Integer rating, String comment) {
}
