package com.zhu.access.api.dto.internal;

/**
 * 接入层 DTO：内部唤醒学习入口入参。
 * 不替代用户登录身份；仅供持有唤醒令牌的调用方打一枪 enqueue。
 */
public record WakeupRequest(
        String userId,
        String sessionId,
        String agentId
) {
}
