package com.zhu.bootstrap;

import com.zhu.audit.api.AuditRecorder;
import com.zhu.common.exception.BusinessException;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.SyncAgentTaskHandler;
import com.zhu.runtime.task.SyncTaskPublisher;
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
        SyncTaskPublisher publisher = new SyncTaskPublisher(new SyncAgentTaskHandler(audit));

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
        SyncTaskPublisher publisher = new SyncTaskPublisher(new SyncAgentTaskHandler(audit));

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
}
