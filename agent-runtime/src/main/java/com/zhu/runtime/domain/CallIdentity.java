package com.zhu.runtime.domain;

import io.agentscope.core.agent.RuntimeContext;

/**
 * 一次执行的身份。从这次调用的 RuntimeContext 读取，中间件在其它线程上也能拿到，不靠 ThreadLocal。
 *
 * @param tenantId  租户，可空
 * @param userId    用户，可空
 * @param sessionId 会话，可空
 * @param runId     这一轮的运行标识，可空
 */
public record CallIdentity(String tenantId, String userId, String sessionId, String runId) {

    private static final CallIdentity NONE = new CallIdentity(null, null, null, null);

    /** 这次调用没有上下文时的空值。 */
    public static CallIdentity none() {
        return NONE;
    }

    /**
     * 从 RuntimeContext 取身份。上下文为空时返回空身份，不打断这次执行。
     * tenantId、agentId、runId 放在扩展字段里，框架的 userId、sessionId 是一等字段。
     */
    public static CallIdentity from(RuntimeContext context) {
        if (context == null) {
            return NONE;
        }
        Object tenant = context.get("tenantId");
        Object run = context.get("runId");
        return new CallIdentity(
                tenant == null ? null : tenant.toString(),
                context.getUserId(),
                context.getSessionId(),
                run == null ? null : run.toString());
    }
}
