package com.zhu.access.application.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 扫「已批准但还没执行」的删除请求并执行。
 *
 * <p>审批在管理进程（只把 PENDING 改成 APPROVED），执行在用户进程：这里才有对象存储和
 * 向量库。两个进程通过表状态交接，没有 RPC。
 *
 * <p>执行前先把状态从 APPROVED 抢成 RUNNING，抢不到就跳过 —— 多实例部署时同一个请求只
 * 会被执行一次。dry-run 也写 executed_at，避免每个轮询周期重复打印同一份预览；要再预览一次
 * 就把那一行的 executed_at 清空。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class DeletionExecutionWorker {

    private static final Logger log = Logger.getLogger(DeletionExecutionWorker.class.getName());

    private static final int BATCH = 20;

    private static final int NOTE_LIMIT = 512;

    private final JdbcTemplate jdbc;

    private final UserDataPurger purger;

    private final LifecycleProperties properties;

    /** 只依赖 JDBC 与清理器，不注入具体业务服务。 */
    public DeletionExecutionWorker(
            JdbcTemplate jdbc, UserDataPurger purger, LifecycleProperties properties) {
        this.jdbc = jdbc;
        this.purger = purger;
        this.properties = properties;
    }

    /** 定时入口。开关关着时空转，不碰库。 */
    @Scheduled(fixedDelayString = "${agentscope.lifecycle.interval-millis:60000}",
            initialDelayString = "${agentscope.lifecycle.initial-delay-millis:20000}")
    public void scheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException ex) {
            // 一轮失败不影响下一轮；具体请求的失败已经写进 executed_note。
            log.warning("deletion execution round failed");
        }
    }

    /**
     * 执行一轮，返回本轮处理的请求数。测试直接调这个方法，不依赖调度器。
     */
    public int runOnce() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT request_id, tenant_id, user_id, data_type FROM data_deletion_request"
                        + " WHERE status = 'APPROVED' AND executed_at IS NULL"
                        + " ORDER BY requested_at, id LIMIT " + BATCH);
        int processed = 0;
        for (Map<String, Object> row : rows) {
            String requestId = text(row, "request_id");
            int claimed = jdbc.update(
                    "UPDATE data_deletion_request SET status = 'RUNNING'"
                            + " WHERE request_id = ? AND status = 'APPROVED'",
                    requestId);
            if (claimed == 0) {
                continue;
            }
            processed++;
            try {
                PurgeReport report = purger.purge(
                        text(row, "tenant_id"), text(row, "user_id"), text(row, "data_type"));
                finishApproved(requestId, report);
                log.info("deletion executed request=" + requestId
                        + " dryRun=" + report.dryRun()
                        + " rows=" + report.deleted());
            } catch (RuntimeException ex) {
                fail(requestId, ex);
            }
        }
        return processed;
    }

    /** 真删完标 DONE；dry-run 只留预览，状态留在 APPROVED 等运维改配置。 */
    private void finishApproved(String requestId, PurgeReport report) {
        if (report.dryRun()) {
            jdbc.update(
                    "UPDATE data_deletion_request SET status = 'APPROVED',"
                            + " executed_at = CURRENT_TIMESTAMP(3), executed_note = ?"
                            + " WHERE request_id = ?",
                    note("dry-run " + report.summary()), requestId);
            return;
        }
        jdbc.update(
                "UPDATE data_deletion_request SET status = 'DONE',"
                        + " executed_at = CURRENT_TIMESTAMP(3), executed_note = ?"
                        + " WHERE request_id = ?",
                note(report.summary()), requestId);
    }

    /** 失败记 FAILED，留摘要给人工重试（把状态改回 APPROVED 即可重跑）。 */
    private void fail(String requestId, RuntimeException ex) {
        String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        jdbc.update(
                "UPDATE data_deletion_request SET status = 'FAILED',"
                        + " executed_at = CURRENT_TIMESTAMP(3), executed_note = ?"
                        + " WHERE request_id = ?",
                note(reason), requestId);
        log.warning("deletion execution failed request=" + requestId);
    }

    private static String note(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= NOTE_LIMIT ? text : text.substring(0, NOTE_LIMIT);
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }
}
