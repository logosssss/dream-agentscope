package com.zhu.runtime.api.agent;

/**
 * 运行层：本轮用户消息里的一张图（多模态）。
 * 只活在当轮请求里，不进 chat_message 正文；后续轮次不再塞回模型。
 *
 * @param attachmentId 附件标识，便于日志关联
 * @param mimeType     如 image/jpeg；空则调用方按 jpeg 兜底
 * @param base64Data   无 data: 前缀的 Base64 字节
 */
public record AgentImagePart(String attachmentId, String mimeType, String base64Data) {
}
