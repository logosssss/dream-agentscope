package com.zhu.access.api.dto.chat;

/**
 * 历史气泡上的附件摘要。给前端下载与展示文件名。
 *
 * @param attachmentId 附件标识
 * @param fileName     原始文件名
 * @param mimeType     登记 MIME，可空串
 */
public record ChatMessageAttachmentItem(String attachmentId, String fileName, String mimeType) {
}
