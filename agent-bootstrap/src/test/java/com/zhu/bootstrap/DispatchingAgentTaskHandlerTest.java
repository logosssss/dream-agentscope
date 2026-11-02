package com.zhu.bootstrap;

import com.zhu.audit.api.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.DispatchingAgentTaskHandler;
import com.zhu.runtime.task.DocumentIndexExecutor;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 任务分发。不连 Milvus。
 */
class DispatchingAgentTaskHandlerTest {

    @Test
    void routesDocumentIndex() {
        DocumentIndexExecutor indexer = mock(DocumentIndexExecutor.class);
        AtomicBoolean ran = new AtomicBoolean();
        doAnswer(inv -> {
                    ran.set(true);
                    return null;
                })
                .when(indexer)
                .index(any());
        AuditRecorder audit = mock(AuditRecorder.class);
        DispatchingAgentTaskHandler handler = new DispatchingAgentTaskHandler(indexer, audit);
        handler.handle(new AgentTask(
                "t1", DocumentIndexExecutor.TYPE, "", "u", "companion-v1", null, Map.of("docId", "d", "version", 1), Instant.now()));
        assertThat(ran).isTrue();
        verifyNoInteractions(audit);
    }

    @Test
    void unknownTypeAuditsOnly() {
        DocumentIndexExecutor indexer = mock(DocumentIndexExecutor.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        new DispatchingAgentTaskHandler(indexer, audit)
                .handle(new AgentTask("t1", "other", "", "u", "a", null, Map.of(), Instant.now()));
        verify(audit).record("task.handle", "u", "type=other,taskId=t1");
        verifyNoInteractions(indexer);
    }
}
