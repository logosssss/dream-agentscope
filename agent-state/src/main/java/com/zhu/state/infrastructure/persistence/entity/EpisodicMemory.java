package com.zhu.state.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * episodic_memory 的一行。插入走 BaseMapper，建表仍由 Flyway 负责。
 * 不存对话全文。
 */
@TableName("episodic_memory")
public class EpisodicMemory {

    /** 自增主键。插入时留空，由数据库生成，给向量主键用。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户。没有租户时是空串，避免唯一检索出现 NULL。 */
    private String tenantId;

    /** 记忆所属用户。 */
    private String userId;

    /** 来源会话。没有会话时是空串。 */
    private String sessionId;

    /** 短摘要。不存对话全文。 */
    private String summary;

    /** 1 到 5，越高越重要。 */
    private Integer salience;

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

    /** 租户，可能是空串。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。空值应先换成空串。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 来源会话，可能是空串。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。空值应先换成空串。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 短摘要。 */
    public String getSummary() {
        return summary;
    }

    /** 写入短摘要。不要把整段对话放进来。 */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /** 重要程度。 */
    public Integer getSalience() {
        return salience;
    }

    /** 写入重要程度。只接受 1 到 5。 */
    public void setSalience(Integer salience) {
        this.salience = salience;
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
