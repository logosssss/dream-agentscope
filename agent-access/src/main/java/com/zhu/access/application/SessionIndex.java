package com.zhu.access.application;

/**
 * 对话开始时记下会话。接入层用它更新列表，不把正文交给运行层。
 */
public interface SessionIndex {

    /**
     * 记下这一轮。用户或会话为空时什么都不做。
     * MySQL 没打开时实现可以跳过。Redis 写失败不得挡住已经开始的对话。
     */
    void record(String tenantId, String userId, String sessionId, String channel, String text);
}
