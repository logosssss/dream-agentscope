package com.zhu.access.application.chat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 接入层排水闸：重建前挡住新 turn，等在途对话结束（或超时）再执行动作。
 * 与 Gateway 同 session 排队互补；本闸只管「热更换实例」窗口。
 */
public final class ResidentAgentDrain {

    private static final Logger log = Logger.getLogger(ResidentAgentDrain.class.getName());

    private final Object lock = new Object();

    private final AtomicInteger inFlight = new AtomicInteger();

    private boolean draining;

    /** 新 turn 入口：排水中则等待；通过后计入在途。 */
    public void beginTurn() {
        synchronized (lock) {
            while (draining) {
                try {
                    lock.wait(200L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for agent rebuild", ex);
                }
            }
            inFlight.incrementAndGet();
        }
    }

    /** 一轮结束（含流式 finally）。 */
    public void endTurn() {
        synchronized (lock) {
            int left = inFlight.decrementAndGet();
            if (left < 0) {
                inFlight.set(0);
            }
            lock.notifyAll();
        }
    }

    /**
     * 进入排水：新 turn 排队等待；等在途归零或超时后执行 action；结束后放行。
     */
    public void drainAndRun(Duration timeout, Runnable action) {
        if (action == null) {
            return;
        }
        Duration limit = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(120)
                : timeout;
        synchronized (lock) {
            draining = true;
            lock.notifyAll();
            try {
                waitIdle(limit);
                action.run();
            } finally {
                draining = false;
                lock.notifyAll();
            }
        }
    }

    /** 当前是否在排水窗口。单测用。 */
    public boolean draining() {
        synchronized (lock) {
            return draining;
        }
    }

    /** 在途轮数。单测用。 */
    public int inFlight() {
        return inFlight.get();
    }

    private void waitIdle(Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (inFlight.get() > 0) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                log.warning("resident agent drain timed out inFlight=" + inFlight.get());
                return;
            }
            long waitMs = Math.max(1L, Math.min(200L, remainingNanos / 1_000_000L));
            try {
                lock.wait(waitMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while draining resident agents", ex);
            }
        }
    }
}
