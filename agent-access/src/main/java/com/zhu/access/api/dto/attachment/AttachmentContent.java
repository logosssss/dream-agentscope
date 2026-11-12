package com.zhu.access.api.dto.attachment;

/**
 * 已经从对象存储读出的附件正文。控制器再写成下载响应。
 *
 * @param fileName  原始文件名，空时用 file.bin
 * @param mimeType  空时按 octet-stream
 * @param content   完整字节
 */
public record AttachmentContent(String fileName, String mimeType, byte[] content) {
}
