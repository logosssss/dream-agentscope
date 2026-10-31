package com.zhu.bootstrap;

import com.zhu.access.application.MysqlSessionOwnerGate;
import com.zhu.access.application.SessionMetaStore;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 同一会话不能换用户。不连接 MySQL。
 */
class SessionOwnerGateTest {

    /** 没有别人时放行。已有其他用户时冲突。 */
    @Test
    void otherUserIsRejected() {
        SessionMetaStore sessions = mock(SessionMetaStore.class);
        MysqlSessionOwnerGate gate = new MysqlSessionOwnerGate(sessions);

        when(sessions.otherUser("t", "s", "u")).thenReturn(null);
        assertThatCode(() -> gate.requireExclusive("t", "u", "s")).doesNotThrowAnyException();

        when(sessions.otherUser("t", "s", "u2")).thenReturn("u");
        assertThatThrownBy(() -> gate.requireExclusive("t", "u2", "s"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
