package com.zhu.access.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL 关闭时不查会话归属。只给测试用，避免对话还要先准备会话行。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class OpenSessionOwnerGate implements SessionOwnerGate {

    /** 存储没开时不拒绝。正式进程打开 MySQL 后改走表。 */
    @Override
    public void requireExclusive(String tenantId, String userId, String sessionId) {
    }
}
