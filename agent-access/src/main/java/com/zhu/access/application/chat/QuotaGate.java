package com.zhu.access.application.chat;

/**
 * 额度闸的窄接口：只暴露「进入对话前查一次额度」这一步。
 * 实现是 {@code QuotaGuard}；接入层只认这个形状，测试可以给纯 lambda。
 */
public interface QuotaGate {

    /**
     * 额度已用尽、被限流或重复提交时拒绝。
     *
     * @param tenantId  租户
     * @param userId    用户
     * @param action    计入限流的动作名，如 {@code chat} / {@code chat-stream}
     * @param requestId 幂等键来源
     */
    void check(String tenantId, String userId, String action, String requestId);
}
