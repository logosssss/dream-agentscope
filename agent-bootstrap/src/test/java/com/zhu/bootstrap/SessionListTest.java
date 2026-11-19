package com.zhu.bootstrap;

import com.zhu.access.api.dto.chat.SessionDetail;
import com.zhu.access.api.dto.chat.SessionSummary;
import com.zhu.access.application.chat.message.AbsentChatMessageStore;
import com.zhu.access.application.chat.session.SessionListService;
import com.zhu.access.application.chat.session.SessionMetaStore;
import com.zhu.cache.api.CacheFacade;
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
        FakeCacheFacade cache = new FakeCacheFacade();
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
        FakeCacheFacade cache = new FakeCacheFacade();
        cache.kv().put(
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
        SessionListService sessions = new SessionListService(store, new ThrowingCacheFacade(), new AbsentChatMessageStore());

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
        SessionListService sessions = new SessionListService(store, new FakeCacheFacade(), new AbsentChatMessageStore());
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
        SessionListService sessions = new SessionListService(store, new FakeCacheFacade(), new AbsentChatMessageStore());
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

}
