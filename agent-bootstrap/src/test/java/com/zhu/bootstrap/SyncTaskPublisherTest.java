package com.zhu.bootstrap;

import com.zhu.audit.api.event.AuditRecorder;
import com.zhu.common.exception.BusinessException;
import com.zhu.runtime.task.log.AbsentAgentTaskLog;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.AgentTaskHandler;
import com.zhu.runtime.task.log.AgentTaskLog;
import com.zhu.runtime.task.handle.SyncAgentTaskHandler;
import com.zhu.runtime.task.publish.SyncTaskPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 同步发布在当前线程把任务交给消费逻辑，审计不包含 payload。
 */
class SyncTaskPublisherTest {

    /** 合法任务记下类型和任务标识，操作者是用户。 */
    @Test
    void publishRecordsTaskWithoutPayload() {
        List<String> records = new ArrayList<>();
        AuditRecorder audit = (action, actorId, detail) -> records.add(action + "|" + actorId + "|" + detail);
        SyncTaskPublisher publisher = new SyncTaskPublisher(new SyncAgentTaskHandler(audit), new AbsentAgentTaskLog());

        publisher.publish(new AgentTask(
                "task-1",
                "doc-ingest",
                "tenant-a",
                "user-a",
                "product-agent",
                "session-a",
                Map.of("body", "secret"),
                Instant.parse("2026-10-30T02:00:00Z")));

        assertThat(records).containsExactly("task.handle|user-a|type=doc-ingest,taskId=task-1");
    }

    /** 缺任务标识时不调用消费逻辑。 */
    @Test
    void blankTaskIdIsRejected() {
        List<String> records = new ArrayList<>();
        AuditRecorder audit = (action, actorId, detail) -> records.add(action);
        SyncTaskPublisher publisher = new SyncTaskPublisher(new SyncAgentTaskHandler(audit), new AbsentAgentTaskLog());

        assertThatThrownBy(() -> publisher.publish(new AgentTask(
                        " ",
                        "doc-ingest",
                        null,
                        "user-a",
                        null,
                        null,
                        null,
                        Instant.EPOCH)))
                .isInstanceOf(BusinessException.class);
        assertThat(records).isEmpty();
    }

    /** 开始记录失败时不执行任务。 */
    @Test
    void beginFailureSkipsHandler() {
        boolean[] handled = new boolean[1];
        AgentTaskHandler handler = task -> handled[0] = true;
        AgentTaskLog tasks = new AgentTaskLog() {
            /** 模拟落库失败。 */
            @Override
            public void begin(AgentTask task) {
                throw new IllegalStateException("db down");
            }

            /** 不会走到结束。 */
            @Override
            public void finish(String taskId, String status, String errorCode) {
            }
        };

        assertThatThrownBy(() -> new SyncTaskPublisher(handler, tasks).publish(sample()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(handled[0]).isFalse();
    }

    /** 结束记录失败不掩盖已经成功的任务。 */
    @Test
    void finishFailureDoesNotHideSuccess() {
        AgentTaskLog tasks = new AgentTaskLog() {
            /** 开始记录成功。 */
            @Override
            public void begin(AgentTask task) {
            }

            /** 结束记录失败。 */
            @Override
            public void finish(String taskId, String status, String errorCode) {
                throw new IllegalStateException("db down");
            }
        };

        new SyncTaskPublisher(task -> {
        }, tasks).publish(sample());
    }

    /** 测试用的合法任务。参数里的正文不会被断言。 */
    private static AgentTask sample() {
        return new AgentTask("task-1", "doc-ingest", "tenant-a", "user-a", "product-agent", "session-a", Map.of(), Instant.EPOCH);
    }
}
