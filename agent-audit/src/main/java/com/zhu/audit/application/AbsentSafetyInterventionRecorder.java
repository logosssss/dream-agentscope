package com.zhu.audit.application;

import com.zhu.audit.api.SafetyInterventionRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写安全干预。只给测试用，不改记到内存。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentSafetyInterventionRecorder implements SafetyInterventionRecorder {

    /** 没有表可写，直接跳过。 */
    @Override
    public void record(
            String tenantId,
            String userId,
            String sessionId,
            String riskLevel,
            String source,
            String summary) {
    }
}
