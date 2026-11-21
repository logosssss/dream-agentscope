package com.zhu.bootstrap;

import com.zhu.access.application.lifecycle.DeletionExecutionWorker;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 删除请求「批准后真执行」的端到端验证：真 MySQL + 真 Redis，
 * 直接调执行器，断言 PII 行消失、审计被脱敏、请求落 DONE。
 *
 * <p>不覆盖附件字节与向量：本机与 CI 都没有 Milvus，附件用 local 存储且对象不存在，
 * 执行器对缺失对象是「忽略」语义，这里验证的是数据库侧的硬删与脱敏。
 *
 * <p>本机没有 Docker 时整类跳过，本地 {@code mvn test} 仍然绿。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=true",
        "agentscope.cache.redis.enabled=true",
        "agentscope.caller.token=e2e-caller-token",
        "agentscope.auth.tenant-id=demo",
        "agentscope.object-store.type=local",
        "agentscope.object-store.root=target/e2e-objects",
        "spring.datasource.hikari.maximum-pool-size=4",
        // 打开执行、关掉 dry-run：这条用例要证明真的删了行。
        "agentscope.lifecycle.enabled=true",
        "agentscope.lifecycle.dry-run=false",
        // 把定时器推远，避免和用例里的显式调用抢同一批请求。
        "agentscope.lifecycle.initial-delay-millis=3600000",
        "agentscope.lifecycle.interval-millis=3600000",
        "agentscope.lifecycle.retention-interval-millis=3600000"
})
class DataLifecycleE2ETest {

    private static final String TENANT = "demo";

    private static final String USER = "u-lifecycle";

    private static final String SESSION = "s-lifecycle";

    private static final String REQUEST = "r-lifecycle";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dream_lifecycle")
            .withUsername("e2e")
            .withPassword("e2e");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("agentscope.cache.redis.host", REDIS::getHost);
        registry.add("agentscope.cache.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("agentscope.cache.redis.password", () -> "");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeletionExecutionWorker worker;

    /** 每个用例从干净的一批行开始：先删后插，避免重复执行时残留。 */
    @BeforeEach
    void seed() {
        Integer tenants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE tenant_id = ?", Integer.class, TENANT);
        if (tenants != null && tenants == 0) {
            jdbc.update("INSERT INTO tenant (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                    TENANT, "生命周期测试租户");
        }
        jdbc.update("DELETE FROM agent_run_step WHERE run_id = 'run-lifecycle'");
        jdbc.update("DELETE FROM agent_run WHERE run_id = 'run-lifecycle'");
        jdbc.update("DELETE FROM chat_message WHERE user_id = ?", USER);
        jdbc.update("DELETE FROM agent_session_meta WHERE user_id = ?", USER);
        jdbc.update("DELETE FROM attachment_ref WHERE attachment_id = 'att-lifecycle'");
        jdbc.update("DELETE FROM attachment WHERE attachment_id = 'att-lifecycle'");
        jdbc.update("DELETE FROM agent_event_audit WHERE user_id LIKE ? OR user_id LIKE 'deleted:%'", USER);
        jdbc.update("DELETE FROM login_credential WHERE user_id = ?", USER);
        jdbc.update("DELETE FROM tenant_user WHERE user_id = ?", USER);
        jdbc.update("DELETE FROM data_deletion_request WHERE request_id = ?", REQUEST);

        jdbc.update("INSERT INTO tenant_user (tenant_id, user_id, display_name, status)"
                + " VALUES (?, ?, ?, 'ACTIVE')", TENANT, USER, "待删用户");
        jdbc.update("INSERT INTO login_credential (tenant_id, user_id, login_name, password_salt, password_hash)"
                + " VALUES (?, ?, ?, 'salt', 'hash')", TENANT, USER, "lifecycle-user");
        jdbc.update("INSERT INTO agent_session_meta (user_id, session_id, tenant_id, last_active_at, message_count)"
                + " VALUES (?, ?, ?, CURRENT_TIMESTAMP(3), 1)", USER, SESSION, TENANT);
        jdbc.update("INSERT INTO chat_message (tenant_id, user_id, session_id, seq, role, content, run_id)"
                + " VALUES (?, ?, ?, 1, 'USER', '要删掉的正文', 'run-lifecycle')", TENANT, USER, SESSION);
        jdbc.update("INSERT INTO agent_run (run_id, tenant_id, user_id, session_id, status)"
                + " VALUES ('run-lifecycle', ?, ?, ?, 'SUCCEEDED')", TENANT, USER, SESSION);
        jdbc.update("INSERT INTO agent_run_step (run_id, step_no, step_type, status)"
                + " VALUES ('run-lifecycle', 1, 'MODEL_CALL', 'SUCCEEDED')");
        jdbc.update("INSERT INTO attachment (attachment_id, tenant_id, user_id, file_name,"
                + " storage_type, storage_path, mime_type, status)"
                + " VALUES ('att-lifecycle', ?, ?, 'a.txt', 'local', 'lifecycle/a.txt', 'text/plain', 'ACTIVE')",
                TENANT, USER);
        jdbc.update("INSERT INTO attachment_ref (attachment_id, ref_type, ref_id)"
                + " VALUES ('att-lifecycle', 'MESSAGE', 'm-1')");
        jdbc.update("INSERT INTO agent_event_audit (event_id, event_type, user_id, session_id, source)"
                + " VALUES ('evt-lifecycle', 'CHAT', ?, ?, 'TEST')", USER, SESSION);
        jdbc.update("INSERT INTO data_deletion_request"
                + " (request_id, user_id, tenant_id, data_type, status, reason)"
                + " VALUES (?, ?, ?, 'ALL', 'APPROVED', '用户申请删除')", REQUEST, USER, TENANT);
    }

    @Test
    void approvedRequestIsExecutedAndPersonalRowsAreGone() {
        int processed = worker.runOnce();
        assertThat(processed).isEqualTo(1);

        Map<String, Object> request = jdbc.queryForMap(
                "SELECT status, executed_at, executed_note FROM data_deletion_request WHERE request_id = ?",
                REQUEST);
        assertThat(request.get("status")).isEqualTo("DONE");
        assertThat(request.get("executed_at")).isNotNull();
        assertThat(String.valueOf(request.get("executed_note"))).contains("deleted=");

        assertThat(count("chat_message")).isZero();
        assertThat(count("agent_session_meta")).isZero();
        assertThat(count("agent_run")).isZero();
        assertThat(count("agent_run_step")).isZero();
        assertThat(count("attachment")).isZero();
        assertThat(count("attachment_ref")).isZero();
        assertThat(count("login_credential")).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM tenant_user WHERE tenant_id = ? AND user_id = ?",
                String.class, TENANT, USER)).isEqualTo("DELETED");

        // 审计保留行，但 user_id 换成不可逆摘要。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_event_audit WHERE user_id LIKE 'deleted:%'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_event_audit WHERE user_id = ?", Integer.class, USER))
                .isZero();
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, USER);
        return value == null ? 0 : value;
    }
}
