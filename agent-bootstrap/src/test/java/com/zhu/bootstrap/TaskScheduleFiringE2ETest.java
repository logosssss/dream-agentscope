package com.zhu.bootstrap;

import com.zhu.common.util.UtcTimes;
import com.zhu.access.application.schedule.TaskScheduleFirer;
import java.time.LocalDateTime;
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
 * 真 MySQL + 真 Redis 上的一条调度触发链路：到期的行被取出 → 推进下次触发 → 发布一条任务
 * → 任务管线落 {@code agent_task} 并记审计 → 触发结果写回调度行。
 *
 * <p>不起 Web 层，也不需要模型：这条链路证明的是「调度真的会触发一次」，
 * 而不是「后端能回答一次对话」（那条在 {@link MySqlRedisChatE2ETest}）。
 *
 * <p>本机没有 Docker 时整个类跳过，CI 的 Linux runner 会真跑。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=true",
        "agentscope.cache.redis.enabled=true",
        "agentscope.caller.token=e2e-caller-token",
        "agentscope.auth.tenant-id=demo",
        "agentscope.object-store.type=local",
        "agentscope.object-store.root=target/e2e-schedule-objects",
        // 触发面关着；用例直接调 runOnce()，不让定时线程干扰断言。
        "agentscope.schedule.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=4"
})
class TaskScheduleFiringE2ETest {

    private static final String TENANT = "demo";

    private static final String SCHEDULE_ID = "sch-e2e-report";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dream_schedule_e2e")
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
    private TaskScheduleFirer firer;

    /** 一条已到期的调度。cron 用每分钟，触发后下次触发一定晚于现在。 */
    @BeforeEach
    void seedDueSchedule() {
        jdbc.update("DELETE FROM agent_task_schedule WHERE schedule_id = ?", SCHEDULE_ID);
        jdbc.update("DELETE FROM agent_task WHERE task_id LIKE ?", "sched-" + SCHEDULE_ID + "@%");
        jdbc.update("""
                        INSERT INTO agent_task_schedule
                            (schedule_id, task_type, tenant_id, user_id, agent_id, cron_expr,
                             enabled, next_fire_at, fire_count)
                        VALUES (?, 'REPORT', ?, 'u-schedule', 'companion-v1', '0 * * * * *', 1, ?, 0)
                        """,
                SCHEDULE_ID, TENANT, UtcTimes.now().minusMinutes(1));
    }

    @Test
    void firesDueScheduleAndRecordsTask() {
        int fired = firer.runOnce();

        assertThat(fired).isEqualTo(1);

        Map<String, Object> schedule = jdbc.queryForMap(
                "SELECT next_fire_at, last_fire_at, last_status, fire_count "
                        + "FROM agent_task_schedule WHERE schedule_id = ?", SCHEDULE_ID);
        assertThat(schedule.get("last_status")).isEqualTo("SUCCEEDED");
        assertThat(((Number) schedule.get("fire_count")).intValue()).isEqualTo(1);
        assertThat(schedule.get("last_fire_at")).isNotNull();
        assertThat(schedule.get("next_fire_at")).isNotNull();
        assertThat(toLocal(schedule.get("last_fire_at")))
                .isBefore(toLocal(schedule.get("next_fire_at")));

        Map<String, Object> task = jdbc.queryForMap(
                "SELECT task_id, task_type, tenant_id, user_id, status FROM agent_task "
                        + "WHERE task_id LIKE ? ORDER BY id DESC LIMIT 1",
                "sched-" + SCHEDULE_ID + "@%");
        assertThat(task.get("status")).isEqualTo("SUCCEEDED");
        assertThat(task.get("task_type")).isEqualTo("REPORT");
        assertThat(task.get("tenant_id")).isEqualTo(TENANT);
        assertThat(task.get("user_id")).isEqualTo("u-schedule");

        // REPORT 执行器留一行审计：动作名是 task.report，payload 里是统计口径与数字。
        // 注意列名是 event_type / payload（不是 action / detail），旧断言查错了列。
        Integer audited = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_event_audit"
                        + " WHERE event_type = 'task.report' AND user_id = ? AND payload LIKE ?",
                Integer.class, "u-schedule", "%days=%");
        assertThat(audited).isNotNull().isPositive();
    }

    /** 触发一次之后，同一行不会在同一分钟里被再触发一次（下次触发已经推进到未来）。 */
    @Test
    void doesNotFireAgainRightAway() {
        assertThat(firer.runOnce()).isEqualTo(1);
        assertThat(firer.runOnce()).isZero();
    }

    /** JDBC 取出来的时间可能带时区信息，统一成本地时间再比。 */
    private static LocalDateTime toLocal(Object value) {
        if (value instanceof LocalDateTime local) {
            return local;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toLocalDateTime();
        }
        throw new IllegalStateException("unexpected time type: " + value.getClass());
    }
}
