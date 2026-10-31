package com.zhu.audit.api;

/**
 * 记下一条用户反馈。MySQL 关闭时没有这个实现。
 * 不核对运行是否存在，也不存附件。
 */
public interface FeedbackRecorder {

    /**
     * 评分必须是 1 到 5。用户为空、说明超长或运行标识超长时拒绝。
     */
    void record(String userId, String runId, Integer rating, String comment);
}
