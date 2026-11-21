package com.zhu.runtime.task.handle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.runtime.task.AgentTask;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 分发器只做三件事：按类型找实现、没找到只记审计、类型重复启动即失败。
 */
class DispatchingAgentTaskHandlerTest {

    private final List<String> audits = new ArrayList<>();

    private AgentTask task(String type) {
        return new AgentTask("t-1", type, "demo", "u-1", "companion-v1", null, Map.of(), Instant.now());
    }

    private AuditRecorderStub audit() {
        return (action, actor, detail) -> audits.add(action + "|" + actor + "|" + detail);
    }

    @Test
    void executesRegisteredExecutor() {
        List<String> executed = new ArrayList<>();
        AgentTaskExecutor executor = new AgentTaskExecutor() {
            @Override
            public String type() {
                return "REPORT";
            }

            @Override
            public void execute(AgentTask task) {
                executed.add(task.taskId());
            }
        };
        DispatchingAgentTaskHandler handler = new DispatchingAgentTaskHandler(List.of(executor), audit());

        handler.handle(task("REPORT"));

        assertThat(executed).containsExactly("t-1");
        assertThat(audits).isEmpty();
    }

    @Test
    void unknownTypeStillOnlyAudits() {
        DispatchingAgentTaskHandler handler = new DispatchingAgentTaskHandler(List.of(), audit());

        handler.handle(task("BACKFILL"));

        assertThat(audits).containsExactly("task.handle|u-1|type=BACKFILL,taskId=t-1");
    }

    @Test
    void duplicateTypeFailsAtStartup() {
        AgentTaskExecutor first = new StubExecutor("BATCH");
        AgentTaskExecutor second = new StubExecutor("BATCH");

        assertThatThrownBy(() -> new DispatchingAgentTaskHandler(List.of(first, second), audit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate task executor type: BATCH");
    }

    @Test
    void blankTypeFailsAtStartup() {
        AgentTaskExecutor blank = new StubExecutor(" ");

        assertThatThrownBy(() -> new DispatchingAgentTaskHandler(List.of(blank), audit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task executor type must not be blank");
    }

    /** 只用来占位的执行器；测试不关心它的行为。 */
    private record StubExecutor(String type) implements AgentTaskExecutor {
        @Override
        public void execute(AgentTask task) {
            // 不做事
        }
    }

    /** 审计替身：只收集「动作|操作者|说明」。 */
    private interface AuditRecorderStub extends com.zhu.runtime.api.event.AuditRecorder {
    }
}
