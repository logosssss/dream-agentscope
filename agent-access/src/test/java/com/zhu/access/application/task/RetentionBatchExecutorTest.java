package com.zhu.access.application.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.lifecycle.LifecycleProperties;
import com.zhu.access.application.lifecycle.RetentionSweeper;
import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 批量任务只做一件事：跑一轮清理并把每张表的结果写进审计。
 */
class RetentionBatchExecutorTest {

    private final List<String> audits = new ArrayList<>();

    private final AuditRecorder audit = (action, actor, detail) -> audits.add(action + "|" + actor + "|" + detail);

    private AgentTask task() {
        return new AgentTask("t-1", RetentionBatchExecutor.TYPE, "demo", "u-1", "companion-v1", null, Map.of(), Instant.now());
    }

    /** 直接替换 runOnce，避免真连库。 */
    private RetentionSweeper sweeper(Map<String, Integer> deleted) {
        return new RetentionSweeper(null, new LifecycleProperties()) {
            @Override
            public Map<String, Integer> runOnce() {
                return deleted;
            }
        };
    }

    @Test
    void recordsDeletedRowsPerTableInStableOrder() {
        new RetentionBatchExecutor(sweeper(Map.of("chat_message", 3, "agent_event_audit", 1)), audit).execute(task());

        assertThat(audits).containsExactly("task.batch|u-1|deleted={agent_event_audit=1, chat_message=3}");
    }

    @Test
    void emptyResultStillReports() {
        new RetentionBatchExecutor(sweeper(Map.of()), audit).execute(task());

        assertThat(audits).containsExactly("task.batch|u-1|deleted={}");
    }
}
