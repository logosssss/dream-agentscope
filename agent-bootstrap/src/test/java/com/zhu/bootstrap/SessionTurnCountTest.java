package com.zhu.bootstrap;

import com.zhu.access.infrastructure.persistence.run.AgentRunMapper;
import com.zhu.access.infrastructure.persistence.session.MysqlSessionTurnCount;
import com.zhu.runtime.application.AbsentSessionTurnCount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 成功轮次只数账本。没有表或身份不全时当 0。
 */
class SessionTurnCountTest {

    /** MySQL 关闭时不查库。 */
    @Test
    void absentAlwaysZero() {
        assertThat(new AbsentSessionTurnCount().succeeded("t", "u", "s")).isZero();
    }

    /** 用户或会话为空时不查 Mapper。 */
    @Test
    void blankIdentitySkipsMapper() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        MysqlSessionTurnCount count = new MysqlSessionTurnCount(runs);

        assertThat(count.succeeded("t", " ", "s")).isZero();
        assertThat(count.succeeded("t", "u", null)).isZero();
        verifyNoInteractions(runs);
    }

    /** 租户空按空串去数，和写入时一致。 */
    @Test
    void countsSucceededRows() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        when(runs.countSucceeded("", "u", "s")).thenReturn(20);
        MysqlSessionTurnCount count = new MysqlSessionTurnCount(runs);

        assertThat(count.succeeded(null, "u", "s")).isEqualTo(20);
    }
}
