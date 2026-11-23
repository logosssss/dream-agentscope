package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.runtime.infrastructure.tool.AttachmentReadTool;
import com.zhu.semantic.api.attachment.UserAttachmentTextPort;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * attachment_read 从 RuntimeContext 取身份并委托端口。
 */
class AttachmentReadToolTest {

    @Test
    void readsViaPort() {
        UserAttachmentTextPort port = mock(UserAttachmentTextPort.class);
        when(port.readText("t1", "u1", "att-1")).thenReturn("正文");
        AttachmentReadTool tool = new AttachmentReadTool(provider(port));
        RuntimeContext ctx = RuntimeContext.builder().userId("u1").sessionId("s1").put("tenantId", "t1").build();
        assertThat(tool.read("att-1", ctx)).isEqualTo("正文");
        assertThat(tool.toolId()).isEqualTo(AttachmentReadTool.TOOL_ID);
    }

    @Test
    void missingPortExplains() {
        AttachmentReadTool tool = new AttachmentReadTool(provider(null));
        assertThat(tool.read("att-1", RuntimeContext.builder().userId("u1").sessionId("s1").build()))
                .contains("不可用");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<UserAttachmentTextPort> provider(UserAttachmentTextPort port) {
        ObjectProvider<UserAttachmentTextPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        return provider;
    }
}
