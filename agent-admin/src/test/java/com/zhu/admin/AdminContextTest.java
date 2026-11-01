package com.zhu.admin;

import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRecorder;
import com.zhu.audit.api.AuditQuery;
import com.zhu.audit.api.AuditRecorder;
import com.zhu.audit.api.SafetyInterventionRecorder;
import com.zhu.common.security.CallerTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理进程能单独起来，并且 classpath 上没有执行内核。
 */
@SpringBootTest(properties = {
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=false",
        "agentscope.cache.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class AdminContextTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    /**
     * 存活检查不依赖用户进程。查询和决定可以注入，写入记录器不在这个进程里。
     * 不引用 HarnessAgent，避免这个模块为了断言去依赖 harness。
     */
    @Test
    void startsWithoutExecutionKernel() throws Exception {
        assertThatThrownBy(() -> Class.forName("io.agentscope.harness.agent.HarnessAgent"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(context.getBeansOfType(AuditQuery.class)).hasSize(1);
        assertThat(context.getBeansOfType(ApprovalDecider.class)).hasSize(1);
        assertThat(context.getBeansOfType(AuditRecorder.class)).isEmpty();
        assertThat(context.getBeansOfType(ApprovalRecorder.class)).isEmpty();
        assertThat(context.getBeansOfType(SafetyInterventionRecorder.class)).isEmpty();
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mysql").value("SKIPPED"))
                .andExpect(jsonPath("$.redis").value("SKIPPED"))
                .andExpect(jsonPath("$.milvus").value("SKIPPED"));
        mvc.perform(get("/api/admin/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/admin/info").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"))
                .andExpect(jsonPath("$.data.note").exists());
        mvc.perform(get("/api/admin/audits").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/admin/approvals").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/api/admin/approvals/ap-1/decision")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"PENDING\",\"approver\":\"ops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/admin/model-prices").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/admin/quota-usage")
                        .header(CallerTokens.HEADER, "test-token")
                        .param("tenantId", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/tenant-quotas")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"limit\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/admin/operation-audits").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(get("/api/admin/safety-interventions").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/deletion-requests/req-a/decision")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/operation-audits")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"op-a\",\"operation\":\"CREATE\",\"objectType\":\"tenant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/retention-policies")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataType\":\"SESSION\",\"retainDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/task-schedules")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":\"sch-a\",\"taskType\":\"REPORT\",\"cronExpr\":\"0 0 * * *\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/model-configs")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configId\":\"cfg-a\",\"model\":\"qwen-plus\",\"provider\":\"dashscope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/eval-runs")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":\"set-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/eval-datasets/set-a/cases")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseNo\":1,\"inputText\":\"问\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/eval-datasets")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":\"set-a\",\"name\":\"示例\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/tenants")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"name\":\"示例\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/tenants/t/suspend").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/tenant-users")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"userId\":\"u\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/tenant-users/t/u/disable").header(CallerTokens.HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/billing/payments")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"periodStart\":\"2026-10-01T00:00:00\",\"periodEnd\":\"2026-10-02T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/billing/invoices")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"periodStart\":\"2026-10-01T00:00:00\",\"periodEnd\":\"2026-10-02T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/billing/confirmations")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"periodStart\":\"2026-10-01T00:00:00\",\"periodEnd\":\"2026-10-02T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/billing/drafts")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t\",\"periodStart\":\"2026-10-01T00:00:00\",\"periodEnd\":\"2026-10-02T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        mvc.perform(post("/api/admin/approvals/ap-1/decision")
                        .header(CallerTokens.HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"approver\":\"ops\",\"reason\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
