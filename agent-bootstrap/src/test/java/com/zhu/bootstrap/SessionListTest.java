package com.zhu.bootstrap;

import com.zhu.access.api.dto.SessionDetail;
import com.zhu.access.api.dto.SessionSummary;
import com.zhu.access.application.AbsentChatMessageStore;
import com.zhu.access.application.SessionListService;
import com.zhu.access.application.SessionMetaStore;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话列表先用缓存，缓存不可用时查存储。不连接 Redis 或 MySQL。
 */
class SessionListTest {

    /** 命中缓存后不再查库。新的一轮对话删掉缓存。 */
    @Test
    void cacheHitSkipsStoreUntilNextRecord() {
        FakeAccessCache cache = new FakeAccessCache();
        CountingStore store = new CountingStore();
        store.rows.add(new SessionSummary("s", "你好", "WEB", "2026-10-31T01:02:03"));
        SessionListService sessions = new SessionListService(store, cache, new AbsentChatMessageStore());

        assertThat(sessions.list("t", "u")).containsExactly(store.rows.get(0));
        assertThat(sessions.list("t", "u")).containsExactly(store.rows.get(0));
        assertThat(store.lists).isEqualTo(1);

        sessions.record("t", "u", "s", "WEB", "下一句\n还在");
        assertThat(store.touches).isEqualTo(1);
        assertThat(store.lastTitle).isEqualTo("下一句 还在");
        sessions.list("t", "u");
        assertThat(store.lists).isEqualTo(2);
    }

    /** 事先写入的缓存按租户和用户分开。冒号不能留在键里。 */
    @Test
    void cacheKeyIncludesTenant() {
        FakeAccessCache cache = new FakeAccessCache();
        cache.put(
                "session-list:a_b:u",
                "[{\"sessionId\":\"cached\",\"title\":\"c\",\"channel\":\"WEB\",\"lastActiveAt\":\"\"}]",
                Duration.ofMinutes(5));
        CountingStore store = new CountingStore();
        SessionListService sessions = new SessionListService(store, cache, new AbsentChatMessageStore());

        assertThat(sessions.list("a:b", "u")).extracting(SessionSummary::sessionId).containsExactly("cached");
        assertThat(store.lists).isZero();
    }

    /** Redis 不可用时仍然返回库里的列表，并且记会话不会失败。 */
    @Test
    void redisOutageFallsBackToStore() {
        CountingStore store = new CountingStore();
        store.rows.add(new SessionSummary("s", "hi", "API", "2026-10-31T00:00:00"));
        SessionListService sessions = new SessionListService(store, new ThrowingCache(), new AbsentChatMessageStore());

        assertThat(sessions.list("t", "u")).containsExactly(store.rows.get(0));
        sessions.record("t", "u", "s", "API", "x".repeat(50));
        assertThat(store.lastTitle).hasSize(40);
        assertThat(store.touches).isEqualTo(1);
    }

    /** 详情走存储，不读列表缓存。空标识拒绝。 */
    @Test
    void getReadsStoreAndRejectsBlank() {
        CountingStore store = new CountingStore();
        store.detail = new SessionDetail("s", "hi", "WEB", "ACTIVE", "run-1", 3, "2026-10-31T01:02:03");
        SessionListService sessions = new SessionListService(store, new FakeAccessCache(), new AbsentChatMessageStore());
        assertThat(sessions.get("t", "u", "s")).isEqualTo(store.detail);
        assertThat(store.owned).isEqualTo(1);
        assertThatThrownBy(() -> sessions.get("t", "u", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        store.detail = null;
        assertThatThrownBy(() -> sessions.get("t", "u", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 用户或会话为空时不写存储。 */
    @Test
    void blankIdentityIsSkipped() {
        CountingStore store = new CountingStore();
        SessionListService sessions = new SessionListService(store, new FakeAccessCache(), new AbsentChatMessageStore());
        sessions.record("t", " ", "s", "WEB", "hi");
        sessions.record("t", "u", "", "WEB", "hi");
        assertThat(store.touches).isZero();
    }

    /** 测试里的存储。记下调用次数和最后一次标题。 */
    private static final class CountingStore implements SessionMetaStore {

        private final List<SessionSummary> rows = new ArrayList<>();

        private int lists;

        private int touches;

        private int owned;

        private String lastTitle;

        private SessionDetail detail;

        /** 记下标题，供断言截断和换行。 */
        @Override
        public void touch(String tenantId, String userId, String sessionId, String channel, String title) {
            touches++;
            lastTitle = title;
        }

        /** 返回预先放好的行。 */
        @Override
        public List<SessionSummary> list(String tenantId, String userId) {
            lists++;
            return List.copyOf(rows);
        }

        /** 运行标识不在这次测试里。 */
        @Override
        public void attachRun(String tenantId, String userId, String sessionId, String runId) {
        }

        /** 返回预先放好的详情。 */
        @Override
        public SessionDetail findOwned(String tenantId, String userId, String sessionId) {
            owned++;
            return detail;
        }
    }

    /** 会话缓存的读写都视为 Redis 不可用。 */
    private static final class ThrowingCache implements AccessCache {

        /** 计数不参与这次测试。 */
        @Override
        public long increment(String key, Duration ttl) {
            throw unavailable();
        }

        @Override
        public long incrementBy(String key, long delta, Duration ttl) {
            throw unavailable();
        }

        @Override
        public long add(String key, long delta, Duration ttl) {
            throw unavailable();
        }

        @Override
        public long getCounter(String key) {
            throw unavailable();
        }

        /** 占位不参与这次测试。 */
        @Override
        public boolean claim(String key, Duration ttl) {
            throw unavailable();
        }

        /** 加锁不参与这次测试。 */
        @Override
        public boolean lock(String key, String token, Duration ttl) {
            throw unavailable();
        }

        /** 解锁不参与这次测试。 */
        @Override
        public void unlock(String key, String token) {
            throw unavailable();
        }

        /** 读缓存失败。 */
        @Override
        public String get(String key) {
            throw unavailable();
        }

        /** 回填失败。 */
        @Override
        public void put(String key, String value, Duration ttl) {
            throw unavailable();
        }

        /** 失效失败。 */
        @Override
        public void delete(String key) {
            throw unavailable();
        }

        /** 和线上 Redis 失败同一错误码。 */
        private static BusinessException unavailable() {
            return new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }
    }
}
