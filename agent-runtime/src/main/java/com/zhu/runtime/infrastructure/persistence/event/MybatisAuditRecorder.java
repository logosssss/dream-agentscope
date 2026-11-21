package com.zhu.runtime.infrastructure.persistence.event;

import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.readmodel.entity.event.AgentEventAudit;
import com.zhu.readmodel.persistence.event.AgentEventAuditMapper;
import com.zhu.common.util.Ids;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 用 BaseMapper 写审计。只在 MySQL 打开时创建，和进程内实现互斥。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisAuditRecorder implements AuditRecorder {

    private final AgentEventAuditMapper events;

    public MybatisAuditRecorder(AgentEventAuditMapper events) {
        this.events = events;
    }

    /**
     * 空字段保持 null，插入策略会跳过它们，让表默认值生效。
     * 来源固定为 EXECUTOR。payload 只包一层 detail。
     */
    @Override
    public void record(String action, String actorId, String detail) {
        write(action, actorId, null, null, detail);
    }

    /**
     * 带会话与运行标识的写法。表里本来就有这两列，写入后审计行才能和 agent_run 对上。
     */
    @Override
    public void record(
            String action, String actorId, String sessionId, String runId, String detail) {
        write(action, actorId, blankToNull(sessionId), blankToNull(runId), detail);
    }

    private void write(
            String action, String actorId, String sessionId, String runId, String detail) {
        AgentEventAudit row = new AgentEventAudit();
        row.setEventId(Ids.newId());
        row.setEventType(action);
        row.setUserId(blankToNull(actorId));
        row.setSessionId(sessionId);
        row.setRunId(runId);
        row.setSource("EXECUTOR");
        row.setPayload(payload(detail));
        events.insert(row);
    }

    /** 空操作者写成 null，列保持空。 */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** 拼成合法 JSON。说明里的引号和换行要转义。 */
    private static String payload(String detail) {
        String text = detail == null ? "" : detail;
        return "{\"detail\":\"" + escape(text) + "\"}";
    }

    /** JSON 字符串转义。只处理会破坏引号的字符。 */
    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
