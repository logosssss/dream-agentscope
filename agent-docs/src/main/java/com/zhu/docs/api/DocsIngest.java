package com.zhu.docs.api;

/**
 * 全量同步。同一进程里已有一轮在跑时，再次调用返回那一轮的 id。
 */
public interface DocsIngest {

    /**
     * 开始或并入当前这一轮。返回的 id 立刻可查状态。
     */
    long start();

    /** 最近一次同步。还没跑过时状态是 {@code NONE}。 */
    DocsSyncStatus status();
}
