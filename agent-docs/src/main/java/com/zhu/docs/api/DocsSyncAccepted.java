package com.zhu.docs.api;

/**
 * 同步已经接上。id 是这一轮或正在跑的那一轮。
 *
 * @param runId docs_sync_run 主键
 */
public record DocsSyncAccepted(long runId) {
}
