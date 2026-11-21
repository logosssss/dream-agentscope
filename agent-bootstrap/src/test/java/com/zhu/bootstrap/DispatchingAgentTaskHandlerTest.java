package com.zhu.bootstrap;

import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.DispatchingAgentTaskHandler;
import com.zhu.runtime.task.handle.DocumentIndexExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务分发。不连 Milvus。
 *
 * <p>执行器是 mock，分发器只认它声明的类型；构造时会读一次 {@code type()}，
 * 所以「没被调用」要按 {@code execute} 断言，不能按「零交互」断言。
 */
class DispatchingAgentTaskHandlerTest {

    @Test
    void routesDocumentIndex() {
        DocumentIndexExecutor indexer = mock(DocumentIndexExecutor.class);
        when(indexer.type()).thenReturn(DocumentIndexExecutor.TYPE);
        AtomicBoolean ran = new AtomicBoolean();
        doAnswer(inv -> {
                    ran.set(true);
                    return null;
                })
                .when(indexer)
                .execute(any());
        AuditRecorder audit = mock(AuditRecorder.class);
        DispatchingAgentTaskHandler handler = new DispatchingAgentTaskHandler(List.of(indexer), audit);
        handler.handle(new AgentTask(
                "t1", DocumentIndexExecutor.TYPE, "", "u", "companion-v1", null, Map.of("docId", "d", "version", 1), Instant.now()));
        assertThat(ran).isTrue();
        verify(audit, never()).record(any(), any(), any());
    }

    @Test
    void unknownTypeAuditsOnly() {
        DocumentIndexExecutor indexer = mock(DocumentIndexExecutor.class);
        when(indexer.type()).thenReturn(DocumentIndexExecutor.TYPE);
        AuditRecorder audit = mock(AuditRecorder.class);
        new DispatchingAgentTaskHandler(List.of(indexer), audit)
                .handle(new AgentTask("t1", "other", "", "u", "a", null, Map.of(), Instant.now()));
        verify(audit).record("task.handle", "u", "type=other,taskId=t1");
        verify(indexer, never()).execute(any());
    }
}
