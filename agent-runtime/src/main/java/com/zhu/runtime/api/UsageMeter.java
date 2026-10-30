package com.zhu.runtime.api;

/**
 * 一次模型调用的用量出口。运行层在回复带 token 数时调用，计费实现不能反向依赖执行细节。
 */
public interface UsageMeter {

    /**
     * 记下这一次的模型名和 token。没有用量时不要调用，避免写成全 0。
     *
     * @param userId        调用用户，可为空
     * @param model         模型名
     * @param inputTokens   输入 token
     * @param outputTokens  输出 token
     * @param latencyMillis 从发起到拿到回复的毫秒数
     */
    void onCall(String userId, String model, int inputTokens, int outputTokens, long latencyMillis);
}
