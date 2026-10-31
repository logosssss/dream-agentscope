package com.zhu.access.application;

/**
 * 同一会话不能换用户继续聊。人格仍是同一份，切开的是会话归属。
 */
public interface SessionOwnerGate {

    /**
     * 会话已属于别人时拒绝。用户或会话为空时由调用方先拦。
     * MySQL 关闭时实现可以放行。
     */
    void requireExclusive(String tenantId, String userId, String sessionId);
}
