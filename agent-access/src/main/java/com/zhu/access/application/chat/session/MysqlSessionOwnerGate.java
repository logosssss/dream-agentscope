package com.zhu.access.application.chat.session;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 按 tenant + session 看是否已经被别人占用。只在存储打开时创建。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlSessionOwnerGate implements SessionOwnerGate {

    private final SessionMetaStore sessions;

    /** 查到其他用户就拒绝，不改已有行。 */
    public MysqlSessionOwnerGate(SessionMetaStore sessions) {
        this.sessions = sessions;
    }

    /**
     * 用户或会话为空时跳过，由对话入口先拒绝空用户。
     * 空白租户按空串，和会话表唯一键对齐。
     */
    @Override
    public void requireExclusive(String tenantId, String userId, String sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String other = sessions.otherUser(tenantId == null ? "" : tenantId, sessionId, userId);
        if (other != null && !other.isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "session belongs to another user");
        }
    }
}
