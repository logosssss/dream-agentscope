package com.zhu.access.api.dto.account;

/**
 * 一项额度的已用量和上限。不改计数。
 *
 * @param used  当前已用量。没有键时是 0
 * @param limit 上限。没有单独配置时为空；0 表示不限额
 */
public record QuotaMeter(long used, Long limit) {
}
