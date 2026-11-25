package com.zhu.runtime.infrastructure.gateway;

import java.util.function.Supplier;

/**
 * ChatUi 与 API 共用一次懒启动。运行层网关内部，不对外暴露。
 */
final class StartedChannel {

    /** 只提供静态方法，避免两条通道各写一遍双检锁。 */
    private StartedChannel() {
    }

    /**
     * 已发布则直接返回。否则持同一把锁启动，再读一次。
     * {@code current} 每次都读最新字段。启动抛出的异常原样冒出。
     * 启动后字段仍为空，按 {@code missing} 失败，避免调用方拿到 null。
     */
    static <T> T open(Object lock, Supplier<T> current, Runnable start, String missing) {
        T open = current.get();
        if (open != null) {
            return open;
        }
        synchronized (lock) {
            if (current.get() == null) {
                start.run();
            }
            open = current.get();
        }
        if (open == null) {
            throw new IllegalStateException(missing);
        }
        return open;
    }
}
