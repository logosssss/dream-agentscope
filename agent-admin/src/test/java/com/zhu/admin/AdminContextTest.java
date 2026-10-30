package com.zhu.admin;

import com.zhu.audit.api.ApprovalRecorder;
import com.zhu.audit.api.AuditQuery;
import com.zhu.audit.api.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理进程能单独起来，并且 classpath 上没有执行内核。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminContextTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    /**
     * 存活检查不依赖用户进程。查询可以注入，写入记录器不在这个进程里。
     * 不引用 HarnessAgent，避免这个模块为了断言去依赖 harness。
     */
    @Test
    void startsWithoutExecutionKernel() throws Exception {
        assertThatThrownBy(() -> Class.forName("io.agentscope.harness.agent.HarnessAgent"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(context.getBeansOfType(AuditQuery.class)).hasSize(1);
        assertThat(context.getBeansOfType(AuditRecorder.class)).isEmpty();
        assertThat(context.getBeansOfType(ApprovalRecorder.class)).isEmpty();
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/admin/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"))
                .andExpect(jsonPath("$.data.note").exists());
        mvc.perform(get("/api/admin/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/admin/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
