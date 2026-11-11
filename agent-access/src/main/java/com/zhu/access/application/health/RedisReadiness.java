package com.zhu.access.application.health;

import com.zhu.common.health.ReadinessSnapshot;
import java.util.logging.Logger;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis 是否能接流量。只在缓存开关打开时探测，不把额度计数写进探测。
 */
public final class RedisReadiness {

    private static final Logger log = Logger.getLogger(RedisReadiness.class.getName());

    private RedisReadiness() {
    }

    /**
     * 开关关闭时返回 SKIPPED。打开后没有连接工厂、PING 不是 PONG 或抛错，都是 DOWN。
     * 响应里不带回异常原文，避免把地址或口令写进就绪接口。
     */
    public static String status(boolean enabled, RedisConnectionFactory factory) {
        if (!enabled) {
            return ReadinessSnapshot.SKIPPED;
        }
        if (factory == null) {
            return ReadinessSnapshot.DOWN;
        }
        try (RedisConnection connection = factory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong) ? ReadinessSnapshot.UP : ReadinessSnapshot.DOWN;
        } catch (RuntimeException ex) {
            log.warning("redis readiness check failed");
            return ReadinessSnapshot.DOWN;
        }
    }
}
