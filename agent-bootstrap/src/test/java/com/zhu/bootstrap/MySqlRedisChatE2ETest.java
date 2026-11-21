package com.zhu.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.common.security.CallerTokens;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.runtime.infrastructure.gateway.ChannelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真 MySQL + 真 Redis 的对话链路：HTTP → 过滤器 → GateChain → 执行缝 → 运行记录与气泡落库 → 回读。
 *
 * <p>唯一被替身的是模型调用那一层（{@link ChannelGateway} 的四个执行方法），因为它要真的大模型和网络；
 * 其余全部是真的：身份过滤器、额度与并发闸门、会话索引、{@code chat_message} 双写、controller 与 DTO。
 *
 * <p>本机没有 Docker 时整个类跳过（{@code disabledWithoutDocker = true}），所以本地 {@code mvn test} 仍是绿的；
 * CI 的 Linux runner 有 Docker，会真跑。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=true",
        "agentscope.cache.redis.enabled=true",
        "agentscope.caller.token=e2e-caller-token",
        "agentscope.auth.tenant-id=demo",
        "agentscope.object-store.type=local",
        "agentscope.object-store.root=target/e2e-chat-objects",
        "spring.datasource.hikari.maximum-pool-size=4"
})
@AutoConfigureMockMvc
class MySqlRedisChatE2ETest {

    private static final String TENANT = "demo";

    private static final String CALLER = "e2e-caller-token";

    /** 替身回复。带中文和引号，顺带验证 JSON 编码不会把它弄坏。 */
    private static final String REPLY = "我先记下这句话：\"你好\"。";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dream_chat_e2e")
            .withUsername("e2e")
            .withPassword("e2e");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("agentscope.cache.redis.host", REDIS::getHost);
        registry.add("agentscope.cache.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("agentscope.cache.redis.password", () -> "");
    }

    /**
     * 只包一层，不换类型：{@link com.zhu.runtime.api.approval.PendingAskFacade} 之类按具体类型注入的地方照常成立。
     */
    @MockitoSpyBean
    private ChannelGateway gateway;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void stubModel() {
        when(gateway.execute(any())).thenReturn(new AgentResponse("run-e2e-chat", REPLY));
        when(gateway.executeApi(any())).thenReturn(new AgentResponse("run-e2e-api", REPLY));
        when(gateway.stream(any())).thenReturn(Flux.just(new AgentStreamEvent("text_block_delta", REPLY)));
        when(gateway.streamApi(any())).thenReturn(Flux.just(new AgentStreamEvent("text_block_delta", REPLY)));
        seedTenant();
    }

    /** 注册只认已存在的租户；租户本身由管理端登记，这里直接写一行。 */
    private void seedTenant() {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE tenant_id = ?", Integer.class, TENANT);
        if (existing != null && existing == 0) {
            jdbc.update("INSERT INTO tenant (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                    TENANT, "对话端到端租户");
        }
    }

    @Test
    void oneChatTurnPersistsRunMessagesAndSession() throws Exception {
        String registered = mvc.perform(post("/api/register")
                        .header(CallerTokens.HEADER, CALLER)
                        .contentType("application/json")
                        .content("{\"accountName\":\"chat-user\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(registered).path("data");
        String userId = data.path("userId").asText();
        String token = data.path("accessToken").asText();

        String sent = mvc.perform(post("/api/chat")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"sessionId\":\"s-chat-1\",\"text\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.text").value(REPLY))
                .andExpect(jsonPath("$.data.runId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String runId = json.readTree(sent).path("data").path("runId").asText();

        // 运行记录：成功、触发者 USER、能被本人按 id 读回。
        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_run WHERE run_id = ? AND user_id = ? AND session_id = ?",
                String.class, runId, userId, "s-chat-1")).isEqualTo("SUCCEEDED");
        mvc.perform(get("/api/runs/" + runId)
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        // 气泡：一轮写两条，seq 递增，正文不串。
        assertThat(jdbc.queryForList(
                "SELECT role, seq, content FROM chat_message WHERE session_id = ? ORDER BY seq",
                "s-chat-1"))
                .hasSize(2)
                .extracting(row -> row.get("role"))
                .containsExactly("user", "assistant");
        assertThat(jdbc.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND seq = 1",
                String.class, "s-chat-1")).isEqualTo("你好");
        assertThat(jdbc.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND seq = 2",
                String.class, "s-chat-1")).isEqualTo(REPLY);

        // 会话索引：一条会话、一轮、标题取输入、最近运行回填。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_session_meta WHERE session_id = ? AND user_id = ?",
                Integer.class, "s-chat-1", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT message_count FROM agent_session_meta WHERE session_id = ?",
                Integer.class, "s-chat-1")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT last_run_id FROM agent_session_meta WHERE session_id = ?",
                String.class, "s-chat-1")).isEqualTo(runId);

        // 回读接口：列表能看到会话，详情能看到两条气泡。
        mvc.perform(get("/api/sessions")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value("s-chat-1"))
                .andExpect(jsonPath("$.data[0].title").value("你好"))
                .andExpect(jsonPath("$.data[0].channel").value("WEB"));
        mvc.perform(get("/api/sessions/s-chat-1/messages")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].content").value(REPLY));

        // 额度：只有对话计一次日请求。
        mvc.perform(get("/api/quota")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requests.used").value(1));
    }
}
