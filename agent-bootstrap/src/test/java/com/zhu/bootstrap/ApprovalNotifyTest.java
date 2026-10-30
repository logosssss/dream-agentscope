package com.zhu.bootstrap;

import com.zhu.access.application.ApprovalNotifyAppService;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接入层订阅审批事件。运行层发布，这里只负责收进待推送列表。
 */
@SpringBootTest
class ApprovalNotifyTest {

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private ApprovalNotifyAppService notifications;

    /** 事件到达后能被接入层看到，不要求运行层知道推送实现。 */
    @Test
    void storesApprovalForLaterPush() {
        events.publishEvent(new ApprovalRequestedEvent("approval-1", "shell", "tenant-a", "user-a"));

        assertThat(notifications.pending())
                .extracting(ApprovalRequestedEvent::approvalId)
                .contains("approval-1");
    }
}
