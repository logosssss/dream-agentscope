package com.zhu.runtime.application;

import com.zhu.runtime.api.SessionTurnCount;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时没有运行账本。人设检查保持关闭。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentSessionTurnCount implements SessionTurnCount {

    /** 没有表可查，当作还没满 20 轮。 */
    @Override
    public int succeeded(String tenantId, String userId, String sessionId) {
        return 0;
    }
}
