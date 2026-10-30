package com.zhu.runtime.api;

/**
 * 一次执行的结果。只带回运行标识和文本，不把框架消息类型漏到接入层。
 *
 * @param runId 本次调用标识
 * @param text  模型返回的文本，没有内容时为空串
 */
public record AgentResponse(String runId, String text) {
}
