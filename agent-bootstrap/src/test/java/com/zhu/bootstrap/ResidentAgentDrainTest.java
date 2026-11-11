package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.chat.ResidentAgentDrain;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 排水闸：重建窗口挡住新 turn，在途结束后才跑动作。
 */
class ResidentAgentDrainTest {

    @Test
    void drainWaitsForInFlightThenRuns() throws Exception {
        ResidentAgentDrain drain = new ResidentAgentDrain();
        drain.beginTurn();
        AtomicBoolean ran = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            started.countDown();
            drain.drainAndRun(Duration.ofSeconds(2), () -> {
                ran.set(true);
                assertThat(drain.inFlight()).isZero();
            });
            finished.countDown();
        });
        worker.start();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50L);
        assertThat(ran.get()).isFalse();
        drain.endTurn();
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(ran.get()).isTrue();
        assertThat(drain.draining()).isFalse();
    }

    @Test
    void beginTurnBlocksWhileDraining() throws Exception {
        ResidentAgentDrain drain = new ResidentAgentDrain();
        CountDownLatch inDrain = new CountDownLatch(1);
        CountDownLatch releaseDrain = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger phase = new AtomicInteger(0);
        Thread rebuilder = new Thread(() -> drain.drainAndRun(Duration.ofSeconds(5), () -> {
            phase.set(1);
            inDrain.countDown();
            try {
                releaseDrain.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            phase.set(2);
        }));
        rebuilder.start();
        assertThat(inDrain.await(2, TimeUnit.SECONDS)).isTrue();
        Thread turn = new Thread(() -> {
            drain.beginTurn();
            entered.countDown();
            drain.endTurn();
        });
        turn.start();
        Thread.sleep(80L);
        assertThat(entered.getCount()).isEqualTo(1L);
        assertThat(phase.get()).isEqualTo(1);
        releaseDrain.countDown();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        rebuilder.join(2000L);
        turn.join(2000L);
        assertThat(drain.inFlight()).isZero();
    }

    @Test
    void drainTimesOutWhenInFlightStuck() {
        ResidentAgentDrain drain = new ResidentAgentDrain();
        drain.beginTurn();
        AtomicBoolean ran = new AtomicBoolean(false);
        // 不 endTurn：超时后仍执行动作，避免热更永久卡住。
        drain.drainAndRun(Duration.ofMillis(80), () -> ran.set(true));
        assertThat(ran.get()).isTrue();
        drain.endTurn();
    }
}
