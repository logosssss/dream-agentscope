package com.zhu.access.application;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 同一租户、用户、会话同时只允许一轮对话。锁在 Redis，调用结束就释放。
 * 不把对话内容放进缓存。进程崩溃后靠过期时间放开，避免会话被永久占住。
 */
@Service
public class SessionTurnGuard {

    /** 比单次调用超时多留的时间，避免调用还没结束锁就过期。 */
    private static final Duration EXTRA = Duration.ofSeconds(30);

    private final AccessCache cache;

    private final Duration ttl;

    /**
     * 超时小于 1 时按 120 秒，和运行配置的缺省一致。
     */
    public SessionTurnGuard(
            AccessCache cache,
            @Value("${agentscope.runtime.timeout-seconds:120}") int timeoutSeconds) {
        int seconds = timeoutSeconds < 1 ? 120 : timeoutSeconds;
        this.cache = cache;
        this.ttl = Duration.ofSeconds(seconds).plus(EXTRA);
    }

    /**
     * 占住这次会话。用户或会话为空时直接拒绝，不占锁。
     * 已经有一轮在进行时抛 CONFLICT。
     */
    public Turn open(String tenantId, String userId, String sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId and sessionId are required");
        }
        String token = Ids.newId();
        String key = "turn:" + segment(tenantId) + ":" + segment(userId) + ":" + segment(sessionId);
        if (!cache.lock(key, token, ttl)) {
            throw new BusinessException(ErrorCode.CONFLICT, "session is busy");
        }
        return new Turn(key, token);
    }

    /** 用占锁时的令牌释放。重复释放没有副作用。 */
    public void close(Turn turn) {
        cache.unlock(turn.key, turn.token);
    }

    /** 键的分段里不留冒号，避免租户标识把四段拆乱。 */
    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(':', '_');
    }

    /**
     * 一次占锁的凭据。只有它能释放对应的键。
     *
     * @param key   会话锁的键
     * @param token 占锁时生成的令牌
     */
    public record Turn(String key, String token) {
    }
}
