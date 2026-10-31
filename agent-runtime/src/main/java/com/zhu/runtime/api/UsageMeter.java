package com.zhu.runtime.api;

/**
 * 一次模型调用的用量出口。运行层在回复带 token 数时调用，计费实现不能反向依赖执行细节。
 */
public interface UsageMeter {

    /**
     * 记下这一次的模型名和 token。没有用量时不要调用，避免写成全 0。
     * 同步对话的 runId 与 agent_run 相同。流式对话的 agent_run 由接入层另生成，两边可能不同。
     *
     * @param tenantId      租户，可为空
     * @param userId        调用用户，可为空
     * @param sessionId     会话，可为空
     * @param runId         这次运行的标识，可为空
     * @param model         模型名
     * @param inputTokens   输入 token
     * @param outputTokens  输出 token
     * @param latencyMillis 从发起到拿到回复的毫秒数
     */
    void onCall(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            int inputTokens,
            int outputTokens,
            long latencyMillis);
}
