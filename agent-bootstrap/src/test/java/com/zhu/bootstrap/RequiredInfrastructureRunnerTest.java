package com.zhu.bootstrap;

import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用户进程启动门槛。不启动 Spring，也不连真实 MySQL / Redis / Milvus。
 */
class RequiredInfrastructureRunnerTest {

    /** 库能连上但容器里没有 Flyway，按没装迁移拒绝启动。 */
    @Test
    void missingFlywayRefusesStartupAfterMysqlIsUp() throws Exception {
        RequiredInfrastructureRunner runner = runner(upDataSource(), null);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("flyway is required");
    }

    /** 有 Flyway Bean 时不报这一项，继续查后面的依赖。 */
    @Test
    void presentFlywayPassesTheFlywayGate() throws Exception {
        RequiredInfrastructureRunner runner = runner(upDataSource(), mock(Flyway.class));

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis is required");
    }

    /** 库不通时先报库。此时即使没有 Flyway，也不把原因说成迁移。 */
    @Test
    void mysqlDownIsReportedBeforeFlyway() throws Exception {
        DataSource down = mock(DataSource.class);
        when(down.getConnection()).thenThrow(new java.sql.SQLException("down", "08001"));
        RequiredInfrastructureRunner runner = runner(down, null);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mysql is required");
    }

    private static RequiredInfrastructureRunner runner(DataSource dataSource, Flyway flyway) {
        @SuppressWarnings("unchecked")
        ObjectProvider<Flyway> flyways = mock(ObjectProvider.class);
        when(flyways.getIfAvailable()).thenReturn(flyway);
        return new RequiredInfrastructureRunner(
                dataSource,
                flyways,
                mock(RedisConnectionFactory.class),
                "");
    }

    /** 连接校验通过，让门槛走到 Flyway 这一项。 */
    private static DataSource upDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        return dataSource;
    }
}
