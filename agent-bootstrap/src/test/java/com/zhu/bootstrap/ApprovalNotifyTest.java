package com.zhu.bootstrap;

import com.zhu.access.application.ApprovalNotifyAppService;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接入层订阅审批事件，并只把当前用户的通知交到接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalNotifyTest {

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private ApprovalNotifyAppService notifications;

    @Autowired
    private MockMvc mvc;

    /** 通知列表是进程内单例，每条用例先清掉，避免串到下一条。 */
    @BeforeEach
    void resetNotifications() {
        notifications.clear();
    }

    /** 事件到达后能被接入层看到，不要求运行层知道推送实现。 */
    @Test
    void storesApprovalForLaterPush() {
        events.publishEvent(new ApprovalRequestedEvent("approval-1", "shell", "tenant-a", "user-a"));

        assertThat(notifications.pending())
                .extracting(ApprovalRequestedEvent::approvalId)
                .contains("approval-1");
    }

    /** 接口只返回请求头对应的用户和租户。缺用户时拒绝，不把整份列表交出去。 */
    @Test
    void pendingEndpointShowsOnlyCaller() throws Exception {
        events.publishEvent(new ApprovalRequestedEvent("a-1", "shell", "tenant-a", "user-a"));
        events.publishEvent(new ApprovalRequestedEvent("a-2", "shell", "tenant-b", "user-a"));
        events.publishEvent(new ApprovalRequestedEvent("a-3", "http", "tenant-a", "user-b"));

        mvc.perform(get("/api/approvals/pending")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].approvalId").value("a-1"))
                .andExpect(jsonPath("$.data[0].toolId").value("shell"));
        mvc.perform(get("/api/approvals/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
