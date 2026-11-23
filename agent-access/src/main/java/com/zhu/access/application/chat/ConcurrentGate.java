package com.zhu.access.application.chat;

/**
 * 租户并发名额的窄接口：占一个名额、用完归还。
 * 实现是 {@code ConcurrentRunGuard}；没有限额时返回 {@link Slot#idle()} 形状的空槽。
 */
public interface ConcurrentGate {

    /**
     * 占一个名额。超限抛 {@code QUOTA_EXCEEDED}，实现不可用时抛 {@code UNAVAILABLE}。
     */
    Slot open(String tenantId);

    /** 归还名额。空槽或重复释放没有副作用。 */
    void close(Slot slot);

    /** 一次占位。key 为空表示这次没有限额。 */
    interface Slot {

        /** Redis 计数键；没有限额时为 null。 */
        String key();
    }
}
