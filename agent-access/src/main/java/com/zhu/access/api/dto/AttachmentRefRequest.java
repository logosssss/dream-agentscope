package com.zhu.access.api.dto;

/**
 * 把当前用户的附件挂到一条消息或一次运行上。不接收文件。
 *
 * @param refType MESSAGE / RUN
 * @param refId   消息或运行标识
 */
public record AttachmentRefRequest(String refType, String refId) {
}
