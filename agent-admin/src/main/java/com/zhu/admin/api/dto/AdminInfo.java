package com.zhu.admin.api.dto;

/**
 * 管理进程说明。给调用方看这个进程是什么，以及它现在看不到哪些数据。
 *
 * @param role 固定为 admin
 * @param note 和用户进程隔离的原因
 */
public record AdminInfo(String role, String note) {
}
