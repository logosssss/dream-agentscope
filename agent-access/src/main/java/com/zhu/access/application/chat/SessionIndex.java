package com.zhu.access.application.chat;

/**
 * 对话开始时记下会话。接入层用它更新列表，不把正文交给运行层。
 */
public interface SessionIndex {

    /**
     * 记下这一轮。用户或会话为空时什么都不做。
     * MySQL 没打开时实现可以跳过。Redis 写失败不得挡住已经开始的对话。
     */
    void record(String tenantId, String userId, String sessionId, String channel, String text);

    /**
     * 把最近一次运行写到会话上。列表不返回这个字段，所以不删列表缓存。详情走库。
     * 缺省什么都不做，测试里的空实现不用再写一遍。
     */
    default void attachRun(String tenantId, String userId, String sessionId, String runId) {
    }
}
