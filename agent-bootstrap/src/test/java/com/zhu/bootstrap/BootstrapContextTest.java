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
@SpringBootTest(properties = {
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=false",
        "agentscope.cache.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class BootstrapContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    /**
     * 执行入口只有一个 AgentExecutor。Agent 按请求创建，不注册成 Bean。
     * 存活检查不依赖 MySQL。默认开关关闭时就绪检查跳过 MySQL 和 Redis。
     */
    @Test
    void startsWithoutHarnessSingleton() throws Exception {
        assertThat(context.getBeansOfType(AgentExecutor.class)).hasSize(1);
        assertThat(context.getBeansOfType(HarnessAgent.class)).isEmpty();
        assertThat(context.getBeansOfType(DistributedStore.class)).isEmpty();
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mysql").value("SKIPPED"))
                .andExpect(jsonPath("$.redis").value("SKIPPED"))
                .andExpect(jsonPath("$.milvus").value("SKIPPED"));
    }
}
