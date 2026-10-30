package com.zhu.runtime.api;

/**
 * 流式事件。接入层按 type 区分增量、工具和结束，不接触框架的 AgentEvent。
 *
 * @param type 事件名，小写，例如 text_block_delta、agent_end
 * @param text 这次事件带出的文本。没有文本时为空串，不放提示词全文
 */
public record AgentStreamEvent(String type, String text) {
}
