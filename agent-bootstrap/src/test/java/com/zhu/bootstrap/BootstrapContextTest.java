package com.zhu.bootstrap;

import com.zhu.common.security.CallerTokens;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.semantic.infrastructure.nacos.AbsentPromptSource;
import com.zhu.semantic.infrastructure.nacos.PromptSource;
import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.infrastructure.embed.DashScopeTextEmbedder;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.application.AbsentVectorStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        assertThat(context.getBeansOfType(PromptSource.class)).hasSize(1);
        assertThat(context.getBean(PromptSource.class)).isInstanceOf(AbsentPromptSource.class);
        assertThat(context.getBeansOfType(VectorStore.class)).hasSize(1);
        assertThat(context.getBean(VectorStore.class)).isInstanceOf(AbsentVectorStore.class);
        assertThat(context.getBeansOfType(TextEmbedder.class)).hasSize(1);
        assertThat(context.getBean(TextEmbedder.class)).isInstanceOf(DashScopeTextEmbedder.class);
        assertThat(context.getBeansOfType(MilvusClientV2.class)).isEmpty();
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mysql").value("SKIPPED"))
                .andExpect(jsonPath("$.redis").value("SKIPPED"))
                .andExpect(jsonPath("$.milvus").value("SKIPPED"));
        mvc.perform(get("/api/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/sessions").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/sessions/s1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/quota").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.requests.used").value(0));
        mvc.perform(get("/api/kb/kb-1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/me").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.userId").value("u"));
        mvc.perform(get("/api/me").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/todos").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/todos/1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/todos").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u")
                        .contentType("application/json")
                        .content("{\"todoType\":\"CONFIRM\",\"refId\":\"run-1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/todos/1/done").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/todos/1/cancel").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/notifications").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/notifications/1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/runs").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/runs/run-1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/usage").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/usage/1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/tasks").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/tasks/task-1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/feedback").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/feedback/1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/feedback").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u")
                        .contentType("application/json")
                        .content("{\"rating\":5}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/deletion-requests").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/deletion-requests/req-1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/deletion-requests").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u")
                        .contentType("application/json")
                        .content("{\"dataType\":\"SESSION\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/attachments").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/attachments/att-1").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/attachments/att-1/disable")
                        .header(CallerTokens.HEADER, "test-token")
                        .header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/attachments/att-1/restore")
                        .header(CallerTokens.HEADER, "test-token")
                        .header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/attachments").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u")
                        .contentType("application/json")
                        .content("{\"fileName\":\"a.txt\",\"storagePath\":\"/tmp/a.txt\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(multipart("/api/attachments/file").file("file", "hi".getBytes())
                        .header(CallerTokens.HEADER, "test-token")
                        .header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/attachments/att-1/refs").header(CallerTokens.HEADER, "test-token").header("X-User-Id", "u")
                        .contentType("application/json")
                        .content("{\"refType\":\"RUN\",\"refId\":\"run-1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/attachments/att-1/content")
                        .header(CallerTokens.HEADER, "test-token")
                        .header("X-User-Id", "u"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/login").header(CallerTokens.HEADER, "test-token")
                        .contentType("application/json")
                        .content("{\"tenantId\":\"t\",\"userId\":\"u\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value("u"));
        mvc.perform(post("/api/logout").header(CallerTokens.HEADER, "test-token")
                        .contentType("application/json")
                        .content("{\"accessToken\":\"unused\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
