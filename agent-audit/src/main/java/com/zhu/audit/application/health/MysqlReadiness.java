package com.zhu.audit.application.health;

import com.zhu.common.health.ReadinessSnapshot;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * MySQL 是否能接流量。两个进程都依赖审计模块，探测放在这里，避免管理进程为了这一项再依赖状态层。
 * 只调用连接校验，不跑业务 SQL。
 */
public final class MysqlReadiness {

    private static final Logger log = Logger.getLogger(MysqlReadiness.class.getName());

    private MysqlReadiness() {
    }

    /**
     * 开关关闭时返回 SKIPPED，即使没有数据源也算这项通过。
     * 打开后没有数据源、连接失败或校验不通过，都是 DOWN。超时 1 秒，避免探活拖住接口。
     */
    public static String status(boolean enabled, DataSource dataSource) {
        if (!enabled) {
            return ReadinessSnapshot.SKIPPED;
        }
        if (dataSource == null) {
            return ReadinessSnapshot.DOWN;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? ReadinessSnapshot.UP : ReadinessSnapshot.DOWN;
        } catch (SQLException ex) {
            log.warning("mysql readiness check failed, sqlState=" + ex.getSQLState());
            return ReadinessSnapshot.DOWN;
        }
    }
}
