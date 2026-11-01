package com.zhu.runtime.api;

/**
 * 会话已经成功结束的轮次。人设检查只看这个数，不看进行中的占锁。
 * 读失败按 0，避免误开检查。
 */
public interface SessionTurnCount {

    /**
     * 同一租户、用户、会话里状态为成功的运行条数。
     * 用户或会话为空时返回 0，不查库。
     */
    int succeeded(String tenantId, String userId, String sessionId);
}
