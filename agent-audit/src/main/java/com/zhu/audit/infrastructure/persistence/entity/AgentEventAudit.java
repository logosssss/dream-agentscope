package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * agent_event_audit 的一行。插入走 BaseMapper，建表仍由 Flyway 负责。
 */
@TableName("agent_event_audit")
public class AgentEventAudit {

    /** 自增主键。插入时留空，由数据库生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件标识，业务侧生成，不是主键。 */
    private String eventId;

    /** 动作名，例如 agent.call。 */
    private String eventType;

    /** 操作者。空则不写入，列保持 NULL。 */
    private String userId;

    /** 会话。当前审计接口没有会话字段，插入时留空。 */
    private String sessionId;

    /** 一次运行的标识。当前调用方还没有传入。 */
    private String runId;

    /** 来源。执行内核写入时固定为 EXECUTOR。 */
    private String source;

    /** JSON 短说明，不是提示词全文。 */
    private String payload;

    /** 入库时间。留空时用表上的默认值。 */
    private LocalDateTime createdAt;

    /** 自增主键。 */
    public Long getId() {
        return id;
    }

    /** 回填数据库生成的主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 事件标识。 */
    public String getEventId() {
        return eventId;
    }

    /** 写入事件标识。 */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /** 动作名。 */
    public String getEventType() {
        return eventType;
    }

    /** 写入动作名。 */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /** 操作者，可能为空。 */
    public String getUserId() {
        return userId;
    }

    /** 写入操作者。空串应先换成 null。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 会话，可能为空。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 运行标识，可能为空。 */
    public String getRunId() {
        return runId;
    }

    /** 写入运行标识。 */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /** 来源。 */
    public String getSource() {
        return source;
    }

    /** 写入来源。 */
    public void setSource(String source) {
        this.source = source;
    }

    /** JSON 短说明。 */
    public String getPayload() {
        return payload;
    }

    /** 写入 JSON 短说明。 */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /** 入库时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入入库时间。通常留空。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
