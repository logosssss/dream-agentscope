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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真 MySQL + 真 Redis 的对话链路：HTTP → 过滤器 → GateChain → 执行缝 → 运行记录与气泡落库 → 回读。
 *
 * <p>唯一被替身的是模型调用那一层（{@link ChannelGateway} 的四个执行方法），因为它要真的大模型和网络；
 * 其余全部是真的：身份过滤器、额度与并发闸门、会话索引、{@code chat_message} 双写、controller 与 DTO。
 *
 * <p>覆盖单轮成功、两轮追加、执行失败三种情形。失败那一轮必须验到「运行记 FAILED、气泡一条都不写」——
 * 否则用户会看到一条自己没发过的消息，或者看到上一轮的内容被追加进下一轮。
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

    private static final String SECOND_REPLY = "第二句我也记下了。";

    private static final String FAILED_REQUEST_ID = "req-e2e-chat-failed";

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
        doReturn(new AgentResponse("run-e2e-chat", REPLY)).when(gateway).execute(any());
        doReturn(new AgentResponse("run-e2e-api", REPLY)).when(gateway).executeApi(any());
        doReturn(Flux.just(new AgentStreamEvent("text_block_delta", REPLY))).when(gateway).stream(any());
        doReturn(Flux.just(new AgentStreamEvent("text_block_delta", REPLY))).when(gateway).streamApi(any());
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

    /** 注册一个新账号，返回 {userId, accessToken}。 */
    private String[] register(String accountName) throws Exception {
        String registered = mvc.perform(post("/api/register")
                        .header(CallerTokens.HEADER, CALLER)
                        .contentType("application/json")
                        .content("{\"accountName\":\"" + accountName + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(registered).path("data");
        return new String[] {data.path("userId").asText(), data.path("accessToken").asText()};
    }

    /** 发一轮对话，返回 {runId, text}。 */
    private String[] chat(String token, String sessionId, String text) throws Exception {
        String sent = mvc.perform(post("/api/chat")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"sessionId\":\"" + sessionId + "\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.runId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(sent).path("data");
        return new String[] {data.path("runId").asText(), data.path("text").asText()};
    }

    @Test
    void oneChatTurnPersistsRunMessagesAndSession() throws Exception {
        String[] account = register("chat-user");
        String userId = account[0];
        String token = account[1];

        String[] turn = chat(token, "s-chat-1", "你好");
        String runId = turn[0];

        // 运行记录：成功、触发者 USER、能被本人按 id 读回。
        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_run WHERE run_id = ? AND user_id = ? AND session_id = ?",
                String.class, runId, userId, "s-chat-1")).isEqualTo("SUCCEEDED");
        mvc.perform(get("/api/runs/" + runId)
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        // 审计行跟着这一轮：执行内核写入的 agent.call 系列都带同一个 run_id 与会话。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_event_audit WHERE run_id = ? AND session_id = ?",
                Integer.class, runId, "s-chat-1")).isGreaterThan(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_event_audit"
                        + " WHERE run_id = ? AND event_type = 'agent.call.error'",
                Integer.class, runId)).isZero();

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

    @Test
    void secondTurnAppendsMessagesAndKeepsFirstTitle() throws Exception {
        String[] account = register("chat-user-2");
        String userId = account[0];
        String token = account[1];
        doReturn(
                        new AgentResponse("run-e2e-chat", REPLY),
                        new AgentResponse("run-e2e-chat-2", SECOND_REPLY))
                .when(gateway).execute(any());

        chat(token, "s-chat-2", "你好");
        String[] second = chat(token, "s-chat-2", "再说一句");
        String secondRunId = second[0];
        assertThat(second[1]).isEqualTo(SECOND_REPLY);

        // 两轮四条气泡，seq 连续递增；第二轮不能把第一轮的内容覆盖或重排。
        assertThat(jdbc.queryForList(
                "SELECT seq, role, content FROM chat_message WHERE session_id = ? ORDER BY seq",
                "s-chat-2"))
                .hasSize(4)
                .extracting(row -> row.get("seq"))
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(jdbc.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND seq = 3",
                String.class, "s-chat-2")).isEqualTo("再说一句");
        assertThat(jdbc.queryForObject(
                "SELECT content FROM chat_message WHERE session_id = ? AND seq = 4",
                String.class, "s-chat-2")).isEqualTo(SECOND_REPLY);

        // 会话索引：轮数累加到 2，标题仍取第一轮的输入，最近运行指向第二轮。
        assertThat(jdbc.queryForObject(
                "SELECT message_count FROM agent_session_meta WHERE session_id = ?",
                Integer.class, "s-chat-2")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM agent_session_meta WHERE session_id = ?",
                String.class, "s-chat-2")).isEqualTo("你好");
        assertThat(jdbc.queryForObject(
                "SELECT last_run_id FROM agent_session_meta WHERE session_id = ?",
                String.class, "s-chat-2")).isEqualTo(secondRunId);

        // 两次运行都在，都是 SUCCEEDED；额度累计到 2。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE session_id = ? AND user_id = ? AND status = 'SUCCEEDED'",
                Integer.class, "s-chat-2", userId)).isEqualTo(2);
        mvc.perform(get("/api/quota")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requests.used").value(2));

        // 回读：四条气泡按顺序返回。
        mvc.perform(get("/api/sessions/s-chat-2/messages")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[2].content").value("再说一句"))
                .andExpect(jsonPath("$.data[3].content").value(SECOND_REPLY));
    }

    @Test
    void failedTurnRecordsFailedRunWithoutMessages() throws Exception {
        String[] account = register("chat-user-3");
        String userId = account[0];
        String token = account[1];
        doThrow(new IllegalStateException("model down")).when(gateway).execute(any());

        // 未分类异常不被接入层吞掉，按容器默认的错误响应返回。
        mvc.perform(post("/api/chat")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", FAILED_REQUEST_ID)
                        .contentType("application/json")
                        .content("{\"sessionId\":\"s-chat-3\",\"text\":\"这一轮会失败\"}"))
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("X-Request-Id", FAILED_REQUEST_ID));

        // 运行记为 FAILED，错误码只有异常类型，不带异常原文。
        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_run WHERE session_id = ? AND user_id = ?",
                String.class, "s-chat-3", userId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT error_code FROM agent_run WHERE session_id = ? AND user_id = ?",
                String.class, "s-chat-3", userId)).isEqualTo("IllegalStateException");

        // 请求号落进运行记录：调用方拿着回显的号就能查到这一轮。
        assertThat(jdbc.queryForObject(
                "SELECT request_id FROM agent_run WHERE session_id = ? AND user_id = ?",
                String.class, "s-chat-3", userId)).isEqualTo(FAILED_REQUEST_ID);

        // 失败一轮一条气泡都不写：用户不会看到自己没发过的消息，也不会看到半截回复。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE session_id = ?",
                Integer.class, "s-chat-3")).isZero();

        // 会话索引仍然建了（用户在列表里看得到这次尝试）。
        // message_count 是「尝试过的轮数」：record 在执行前就 +1，失败那一轮也计一次，这是现有口径。
        assertThat(jdbc.queryForObject(
                "SELECT message_count FROM agent_session_meta WHERE session_id = ? AND user_id = ?",
                Integer.class, "s-chat-3", userId)).isEqualTo(1);

        // 额度在失败前已扣：失败也占一次日请求，不能靠失败刷额外次数。
        mvc.perform(get("/api/quota")
                        .header(CallerTokens.HEADER, CALLER)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requests.used").value(1));
    }
}
