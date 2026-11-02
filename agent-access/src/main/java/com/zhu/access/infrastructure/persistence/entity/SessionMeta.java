package com.zhu.access.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * agent_session_meta 的一行。只放列表需要的字段，不放对话正文。
 */
@TableName("agent_session_meta")
public class SessionMeta {

    /** 自增主键。列表不把它交给调用方。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户。和请求里的用户标识一致。 */
    private String userId;

    /** 会话。同一租户下和用户一起唯一。 */
    private String sessionId;

    /** 租户。没有租户时是空串，避免唯一键出现 NULL。 */
    private String tenantId;

    /** 短标题。更新时已有标题不覆盖。 */
    private String title;

    /** WEB、IM 或 API。 */
    private String channel;

    /** ACTIVE 或 ARCHIVED。列表只看活动行。 */
    private String status;

    /** 最近一次运行。会话行还不存在时不会写。 */
    private String lastRunId;

    /** 已记录的对话轮数。touch 时加一。 */
    private Integer messageCount;

    /** 最近活动时间。由写入方填 UTC。 */
    private LocalDateTime lastActiveAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 会话。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 短标题。 */
    public String getTitle() {
        return title;
    }

    /** 写入短标题。 */
    public void setTitle(String title) {
        this.title = title;
    }

    /** 通道。 */
    public String getChannel() {
        return channel;
    }

    /** 写入通道。 */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /** 会话状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入会话状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 最近一次运行。 */
    public String getLastRunId() {
        return lastRunId;
    }

    /** 写入最近一次运行。 */
    public void setLastRunId(String lastRunId) {
        this.lastRunId = lastRunId;
    }

    /** 对话轮数。 */
    public Integer getMessageCount() {
        return messageCount;
    }

    /** 写入对话轮数。 */
    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    /** 最近活动时间。 */
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    /** 写入最近活动时间。 */
    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
