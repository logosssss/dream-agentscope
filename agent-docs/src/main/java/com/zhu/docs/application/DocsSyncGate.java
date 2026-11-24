package com.zhu.docs.application;

/**
 * 进程内只放行一轮同步。锁在内存里，进程重启后不接着上次的 RUNNING。
 */
final class DocsSyncGate {

    private boolean running;

    private long runId;

    /** 已经在跑就返回那一轮的 id，否则返回 null。 */
    synchronized Long currentIfRunning() {
        return running ? runId : null;
    }

    /** 记下这一轮已经开始。必须先确认没有别的轮次。 */
    synchronized void markRunning(long id) {
        running = true;
        runId = id;
    }

    /** 这一轮结束，下一轮才能开始。 */
    synchronized void finish() {
        running = false;
    }
}
