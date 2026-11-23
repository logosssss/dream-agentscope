package com.zhu.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.common.security.CallerTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真 MySQL + 真 Redis 的端到端链路：Flyway 迁移 → 注册 → 登录 → 附件上传下载 → 落库。
 *
 * <p>不覆盖模型调用：CI 没有 DASHSCOPE_API_KEY，一轮对话需要真实大模型。
 * 这里证明的是「数据库表结构、身份、会话、对象存储、额度」这几段能串起来。
 *
 * <p>本机没有 Docker 时整个类跳过（{@code disabledWithoutDocker = true}），
 * 所以本地 {@code mvn test} 仍然是绿的；CI 的 Linux runner 有 Docker，会真跑。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        // 不探测 Milvus：这条链路不碰向量库。
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=true",
        "agentscope.cache.redis.enabled=true",
        // 令牌必须是安全值，占位串会被 CallerTokens 拒收。
        "agentscope.caller.token=e2e-caller-token",
        "agentscope.auth.tenant-id=demo",
        "agentscope.object-store.type=local",
        "agentscope.object-store.root=target/e2e-objects",
        "spring.datasource.hikari.maximum-pool-size=4"
})
@AutoConfigureMockMvc
class MySqlRedisE2ETest {

    private static final String TENANT = "demo";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dream_e2e")
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    /** 注册只认已存在的租户；租户本身由管理端登记，这里直接写一行。 */
    @BeforeEach
    void seedTenant() {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE tenant_id = ?", Integer.class, TENANT);
        if (existing != null && existing == 0) {
            jdbc.update("INSERT INTO tenant (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                    TENANT, "端到端测试租户");
        }
    }

    @Test
    void flywayMigratesRegisterLoginAndAttachmentFlow() throws Exception {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(41);

        String registerBody = mvc.perform(post("/api/register")
                        .header(CallerTokens.HEADER, "e2e-caller-token")
                        .contentType("application/json")
                        .content("{\"accountName\":\"e2e-user\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode registered = json.readTree(registerBody).path("data");
        String userId = registered.path("userId").asText();
        String token = registered.path("accessToken").asText();

        assertThat(userId).isNotBlank();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_user WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE'",
                Integer.class, TENANT, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_credential WHERE tenant_id = ? AND user_id = ? AND login_name = ?",
                Integer.class, TENANT, userId, "e2e-user")).isEqualTo(1);

        // 会话令牌只在 Redis：能读 /api/me 就说明 Redis 这条链路是通的。
        mvc.perform(get("/api/me")
                        .header(CallerTokens.HEADER, "e2e-caller-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT));

        // 重新用账号密码登录，证明 PBKDF2 存的是可验证的哈希。
        mvc.perform(post("/api/login")
                        .header(CallerTokens.HEADER, "e2e-caller-token")
                        .contentType("application/json")
                        .content("{\"accountName\":\"e2e-user\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));

        // 附件：字节走对象存储，元数据落 attachment 表。
        byte[] payload = "hello-e2e".getBytes();
        String uploadBody = mvc.perform(multipart("/api/attachments/file")
                        .file(new MockMultipartFile("file", "hello.txt", "text/plain", payload))
                        .header(CallerTokens.HEADER, "e2e-caller-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String attachmentId = json.readTree(uploadBody).path("data").path("attachmentId").asText();

        assertThat(attachmentId).isNotBlank();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM attachment WHERE attachment_id = ? AND user_id = ?",
                Integer.class, attachmentId, userId)).isEqualTo(1);

        byte[] downloaded = mvc.perform(get("/api/attachments/" + attachmentId + "/content")
                        .header(CallerTokens.HEADER, "e2e-caller-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(payload);

        // 额度：注册与登录都过了限流，日请求数仍应是 0（只有对话才记）。
        mvc.perform(get("/api/quota")
                        .header(CallerTokens.HEADER, "e2e-caller-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.requests.used").value(0));
    }
}
