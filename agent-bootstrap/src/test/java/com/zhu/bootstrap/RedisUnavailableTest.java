package com.zhu.bootstrap;

import com.zhu.access.api.controller.AccessExceptionHandler;
import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.ChatAppService;
import com.zhu.access.application.QuotaGuard;
import com.zhu.access.application.SessionTurnGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.access.infrastructure.cache.RedisAccessCache;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Redis 不可用时额度请求失败，不执行对话，也不改用别的存储。
 */
class RedisUnavailableTest {

    /** 计数失败发生在执行之前。 */
    @Test
    void chatStopsBeforeExecute() {
        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setDailyLimit(1);
        AccessCache cache = new ThrowingCache();
        AgentExecutor executor = mock(AgentExecutor.class);
        ChatAppService chats = new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(cache, limits, Clock.systemUTC()),
                new SessionTurnGuard(cache, 120),
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, started) -> {
                });

        assertThatThrownBy(() -> chats.chat(new ChatRequest(null, "t", "u", "s", null, "hi", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(ErrorCode.UNAVAILABLE);
        verifyNoInteractions(executor);
    }

    /** 连接失败收成 UNAVAILABLE，不把 Redis 地址带出去。 */
    @Test
    void redisFailureBecomesUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("127.0.0.1 down"));
        RedisAccessCache cache = new RedisAccessCache(redis);

        BusinessException error = caught(() -> cache.increment("quota:t", Duration.ofHours(1)));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAVAILABLE);
        assertThat(error.getMessage()).doesNotContain("127.0.0.1");
    }

    /** 依赖不可用是 503。额度用尽仍是 HTTP 200，调用方看错误码。 */
    @Test
    void unavailableUsesHttp503() {
        AccessExceptionHandler handler = new AccessExceptionHandler();

        ResponseEntity<ApiResult<Void>> down = handler.business(
                new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable"));
        ResponseEntity<ApiResult<Void>> quota = handler.business(
                new BusinessException(ErrorCode.QUOTA_EXCEEDED, "daily quota exceeded"));

        assertThat(down.getStatusCode().value()).isEqualTo(503);
        assertThat(down.getBody().code()).isEqualTo("UNAVAILABLE");
        assertThat(quota.getStatusCode().value()).isEqualTo(200);
        assertThat(quota.getBody().code()).isEqualTo("QUOTA_EXCEEDED");
    }

    /** 回复已经生成后，释放锁失败不向外抛。 */
    @Test
    void unlockFailureDoesNotEscape() {
        SessionTurnGuard turns = new SessionTurnGuard(new ThrowingCache(), 120);

        turns.close(new SessionTurnGuard.Turn("turn:t:u:s", "token"));
    }

    /** 取出业务异常。 */
    private static BusinessException caught(Runnable call) {
        try {
            call.run();
        } catch (BusinessException error) {
            return error;
        }
        throw new AssertionError("expected BusinessException");
    }

    /** 所有缓存操作都视为 Redis 不可用。 */
    private static final class ThrowingCache implements AccessCache {

        @Override
        public long increment(String key, Duration ttl) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }

        @Override
        public boolean claim(String key, Duration ttl) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }

        @Override
        public boolean lock(String key, String token, Duration ttl) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }

        @Override
        public void unlock(String key, String token) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }

        @Override
        public String get(String key) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }

        @Override
        public void delete(String key) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }
    }
}
