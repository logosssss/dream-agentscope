package com.zhu.bootstrap;

import com.zhu.access.application.health.RedisReadiness;
import com.zhu.audit.application.health.MysqlReadiness;
import com.zhu.common.health.MilvusProbe;
import com.zhu.common.health.ReadinessSnapshot;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 就绪探测在开关关闭时不连外部依赖。打开后连接失败必须是 DOWN。
 */
class ReadinessProbeTest {

    /** 关闭开关时不向数据源要连接。 */
    @Test
    void mysqlSkipsWhenDisabled() {
        DataSource dataSource = mock(DataSource.class);
        assertThat(MysqlReadiness.status(false, dataSource)).isEqualTo(ReadinessSnapshot.SKIPPED);
        assertThat(MysqlReadiness.status(true, null)).isEqualTo(ReadinessSnapshot.DOWN);
    }

    /** 连接校验通过才是 UP。校验失败或抛错都是 DOWN。 */
    @Test
    void mysqlFollowsConnectionValidity() throws Exception {
        DataSource up = mock(DataSource.class);
        Connection valid = mock(Connection.class);
        when(up.getConnection()).thenReturn(valid);
        when(valid.isValid(1)).thenReturn(true);
        assertThat(MysqlReadiness.status(true, up)).isEqualTo(ReadinessSnapshot.UP);

        DataSource down = mock(DataSource.class);
        when(down.getConnection()).thenThrow(new SQLException("down", "08001"));
        assertThat(MysqlReadiness.status(true, down)).isEqualTo(ReadinessSnapshot.DOWN);
    }

    /** Redis 关闭时不取连接。PING 不是 PONG 时未就绪。 */
    @Test
    void redisRequiresPong() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        assertThat(RedisReadiness.status(false, factory)).isEqualTo(ReadinessSnapshot.SKIPPED);
        assertThat(RedisReadiness.status(true, null)).isEqualTo(ReadinessSnapshot.DOWN);

        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        assertThat(RedisReadiness.status(true, factory)).isEqualTo(ReadinessSnapshot.UP);

        when(connection.ping()).thenReturn("LOADING");
        assertThat(RedisReadiness.status(true, factory)).isEqualTo(ReadinessSnapshot.DOWN);
    }

    /** 空地址或连不上都是不可达。不创建集合。 */
    @Test
    void milvusBlankOrClosedPortIsDown() {
        assertThat(MilvusProbe.reachable(null, 200)).isFalse();
        assertThat(MilvusProbe.reachable("  ", 200)).isFalse();
        assertThat(MilvusProbe.reachable("http://127.0.0.1:1", 200)).isFalse();
    }

    /** 有一项 DOWN 时总状态为 DOWN，HTTP 层据此返回 503。 */
    @Test
    void anyDownMarksSnapshotDown() {
        ReadinessSnapshot snapshot = ReadinessSnapshot.of(Map.of("mysql", "SKIPPED", "redis", "DOWN"));
        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.status()).isEqualTo(ReadinessSnapshot.DOWN);
    }
}
