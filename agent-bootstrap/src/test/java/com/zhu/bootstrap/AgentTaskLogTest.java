package com.zhu.bootstrap;

import com.zhu.access.infrastructure.persistence.task.AgentTaskMapper;
import com.zhu.access.infrastructure.persistence.task.MysqlAgentTaskLog;
import com.zhu.access.infrastructure.persistence.entity.task.AgentTaskRow;
import com.zhu.runtime.task.AgentTask;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 同步任务插入时是执行中，不写任务参数。不连接 MySQL。
 */
class AgentTaskLogTest {

    /** 参数即使有正文也不进插入对象。 */
    @Test
    void beginStoresRunningWithoutPayload() {
        AgentTaskMapper tasks = mock(AgentTaskMapper.class);
        new MysqlAgentTaskLog(tasks).begin(new AgentTask(
                "task-1",
                "doc-ingest",
                "tenant-a",
                "user-a",
                "product-agent",
                " ",
                Map.of("body", "secret"),
                Instant.EPOCH));

        ArgumentCaptor<AgentTaskRow> saved = ArgumentCaptor.forClass(AgentTaskRow.class);
        verify(tasks).insert(saved.capture());
        AgentTaskRow row = saved.getValue();
        assertThat(row.getTaskId()).isEqualTo("task-1");
        assertThat(row.getStatus()).isEqualTo("RUNNING");
        assertThat(row.getSessionId()).isNull();
        assertThat(row.getTaskType()).isEqualTo("doc-ingest");
    }
}
