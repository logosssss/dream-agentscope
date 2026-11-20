package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.cache.api.CacheKeys;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 微信 access_token 键与 FakeCacheFacade 读写契约（不连微信、不连真实 Redis）。
 * 正式回写在 {@code WxWeixin}；此处只锁键形与 TTL 可过期。
 */
class WxAccessTokenCacheTest {

    @Test
    void putsAndReadsTokenByAppIdKey() {
        FakeCacheFacade cache = new FakeCacheFacade();
        String key = CacheKeys.ofCapability("wx", "access-token", "app-demo");
        cache.kv().put(key, "token-1", Duration.ofMinutes(5));
        assertThat(cache.kv().get(key)).isEqualTo("token-1");
        cache.kv().delete(key);
        assertThat(cache.kv().get(key)).isNull();
    }
}
