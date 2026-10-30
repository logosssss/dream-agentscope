package com.zhu.bootstrap;

import com.zhu.runtime.api.AgentExecutor;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 进程能在没有数据库的情况下启动，并且容器里没有 HarnessAgent 单例。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BootstrapContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    /**
     * 执行入口只有一个 AgentExecutor。Agent 按请求创建，不注册成 Bean。
     * 存活检查不依赖 MySQL。
     */
    @Test
    void startsWithoutHarnessSingleton() throws Exception {
        assertThat(context.getBeansOfType(AgentExecutor.class)).hasSize(1);
        assertThat(context.getBeansOfType(HarnessAgent.class)).isEmpty();
        assertThat(context.getBeansOfType(DistributedStore.class)).isEmpty();
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
