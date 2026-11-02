package com.zhu.audit.api;

import java.util.List;

/**
 * 记下并查看用户反馈。MySQL 关闭时没有这个实现。
 * 不核对运行是否存在，也不存附件。
 */
public interface FeedbackRecorder {

    /**
     * 评分必须是 1 到 5。用户为空、说明超长或运行标识超长时拒绝。
     */
    void record(String userId, String runId, Integer rating, String comment);

    /**
     * 当前用户最近的反馈。默认 50 条，最多 100 条。
     * 表没有租户列，不按租户再滤；也不把别人的行带出来。
     */
    List<FeedbackSummary> owned(String userId, int limit);
}
