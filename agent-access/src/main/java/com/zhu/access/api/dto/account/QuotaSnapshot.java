package com.zhu.access.api.dto.account;

/**
 * 当前用户当日额度。计数在 Redis，上限来自租户行或进程配置。
 *
 * @param day        UTC 日期，yyyyMMdd
 * @param requests   当日请求次数。上限没有租户行时用进程配置
 * @param tokens     当日 token。没有租户行时上限为空
 * @param cost       当日费用微单位。没有租户行时上限为空
 * @param concurrent 当前同时运行数。没有租户行时上限为空
 */
public record QuotaSnapshot(
        String day,
        QuotaMeter requests,
        QuotaMeter tokens,
        QuotaMeter cost,
        QuotaMeter concurrent) {
}
