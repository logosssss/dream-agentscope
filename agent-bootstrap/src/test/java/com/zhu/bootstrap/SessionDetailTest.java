package com.zhu.bootstrap;

import com.zhu.access.infrastructure.persistence.MysqlSessionMetaStore;
import com.zhu.access.infrastructure.persistence.SessionMetaMapper;
import com.zhu.access.infrastructure.persistence.entity.SessionMeta;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话详情带最近运行和轮数。不连接 MySQL，也不读正文。
 */
class SessionDetailTest {

    /** 空白租户按空串。没有活动时间时用空串。归档行也返回。 */
    @Test
    void findOwnedMapsWrittenFields() {
        SessionMetaMapper sessions = mock(SessionMetaMapper.class);
        MysqlSessionMetaStore store = new MysqlSessionMetaStore(sessions);
        SessionMeta row = new SessionMeta();
        row.setSessionId("s");
        row.setTitle("hi");
        row.setChannel("WEB");
        row.setStatus("ARCHIVED");
        row.setLastRunId("run-1");
        row.setMessageCount(4);
        row.setLastActiveAt(LocalDateTime.of(2026, 10, 31, 1, 2, 3));
        when(sessions.findOwned("t", "u", "s")).thenReturn(row);
        assertThat(store.findOwned("t", "u", "s")).satisfies(detail -> {
            assertThat(detail.sessionId()).isEqualTo("s");
            assertThat(detail.status()).isEqualTo("ARCHIVED");
            assertThat(detail.lastRunId()).isEqualTo("run-1");
            assertThat(detail.messageCount()).isEqualTo(4);
            assertThat(detail.lastActiveAt()).isEqualTo("2026-10-31T01:02:03");
        });
        verify(sessions).findOwned("t", "u", "s");
        when(sessions.findOwned("", "u", "s2")).thenReturn(null);
        assertThat(store.findOwned(null, "u", "s2")).isNull();
    }
}
