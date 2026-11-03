package com.zhu.access.api.dto;

/**
 * 会话里的一条气泡。按 seq 升序展示。
 *
 * @param seq       会话内序号
 * @param role      user 或 assistant
 * @param content   正文，可空串
 * @param runId     同轮运行标识，可空
 * @param createdAt UTC 时间，没有时区后缀
 */
public record ChatMessageItem(long seq, String role, String content, String runId, String createdAt) {
}
