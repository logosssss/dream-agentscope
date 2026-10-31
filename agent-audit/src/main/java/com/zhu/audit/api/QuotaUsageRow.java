package com.zhu.audit.api;

import java.time.LocalDateTime;

/**
 * 一条已通过的每日请求流水。不带运行标识。
 *
 * @param id        表主键
 * @param tenantId  租户
 * @param userId    用户，没有时为空
 * @param quotaType 这次是 REQUESTS
 * @param amount    消耗量，通过的请求是 1
 * @param reason    入口名，没有时为空
 * @param createdAt 写入时间
 */
public record QuotaUsageRow(
        Long id,
        String tenantId,
        String userId,
        String quotaType,
        long amount,
        String reason,
        LocalDateTime createdAt) {
}
