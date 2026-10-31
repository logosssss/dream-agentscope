package com.zhu.access.application;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写运行记录。只给测试用，不在写库失败时顶上。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentRunLog implements RunLog {

    /** 没有表可写，直接跳过。 */
    @Override
    public void complete(
            String tenantId,
            String userId,
            String sessionId,
            String trigger,
            String status,
            String runId,
            String inputSummary,
            String outputSummary,
            String errorCode,
            List<RunStep> steps,
            long startedNanos) {
    }
}
