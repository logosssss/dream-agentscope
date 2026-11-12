package com.zhu.access.api.dto.account;

/**
 * 当前用户提出的数据删除请求。不触发删除。
 *
 * @param dataType SESSION / ATTACHMENT / ALL
 * @param reason   原因，可以空
 */
public record DeletionRequest(String dataType, String reason) {
}
