package com.zhu.access.application.chat.gateway;

import io.agentscope.harness.agent.gateway.SessionTurnGate;
import io.agentscope.harness.agent.gateway.TurnBusyException;
import io.agentscope.harness.agent.gateway.TurnLease;
import java.util.Objects;

/**
 * 接入层 SessionTurnGate 装饰：把 {@code channel|r:session} 归一成 session 再占轮。
 * 原因：框架 {@code MsgContext.canonicalKey} 含 channel 前缀，裸共享 {@code LocalSessionTurnGate}
 * 时 chatui/api 同 session 会并行。见 docs/api-channel-落地.md。
 */
public final class RoomNormalizedSessionTurnGate implements SessionTurnGate {

    private final SessionTurnGate delegate;

    /**
     * @param delegate 真正占轮的闸（通常为 {@code LocalSessionTurnGate}）
     */
    public RoomNormalizedSessionTurnGate(SessionTurnGate delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * 先抽 room/session，再交给委托闸。
     */
    @Override
    public TurnLease acquire(String sessionKey) throws InterruptedException, TurnBusyException {
        return delegate.acquire(roomKey(sessionKey));
    }

    /**
     * 与 {@link #acquire} 使用同一归一化键。
     */
    @Override
    public boolean isRunning(String sessionKey) {
        return delegate.isRunning(roomKey(sessionKey));
    }

    /**
     * 从 canonicalKey 抽出 {@code |r:} 后的 session；没有该段则原样返回。
     */
    public static String roomKey(String sessionKey) {
        if (sessionKey == null) {
            return "";
        }
        int idx = sessionKey.indexOf("|r:");
        if (idx < 0) {
            return sessionKey;
        }
        String after = sessionKey.substring(idx + 3);
        int next = after.indexOf('|');
        return next < 0 ? after : after.substring(0, next);
    }
}
