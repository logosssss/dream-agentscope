package com.zhu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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

    /**
     * 存活检查不依赖用户进程。说明接口标明看不到内存审计。
     * 不引用 HarnessAgent，避免这个模块为了断言去依赖 harness。
     */
    @Test
    void startsWithoutExecutionKernel() throws Exception {
        assertThatThrownBy(() -> Class.forName("io.agentscope.harness.agent.HarnessAgent"))
                .isInstanceOf(ClassNotFoundException.class);
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/admin/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"))
                .andExpect(jsonPath("$.data.note").exists());
    }
}
