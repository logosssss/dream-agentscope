package com.zhu.audit.infrastructure.persistence.entity.safety;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * safety_intervention 的一行。只放档位和短摘要，不放用户原文。
 */
@TableName("safety_intervention")
public class SafetyIntervention {

    /** 自增主键。这次不返回给调用方。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户。没有时是空串。 */
    private String tenantId;

    /** 被干预的用户。 */
    private String userId;

    /** 会话。没有时是空串。 */
    private String sessionId;

    /** ELEVATED 或 CRITICAL。 */
    private String riskLevel;

    /** KEYWORD、SEMANTIC 或 JUDGE_FAILED。 */
    private String source;

    /** 短摘要。 */
    private String summary;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
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

    /** 会话。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 档位。 */
    public String getRiskLevel() {
        return riskLevel;
    }

    /** 写入档位。 */
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    /** 来源。 */
    public String getSource() {
        return source;
    }

    /** 写入来源。 */
    public void setSource(String source) {
        this.source = source;
    }

    /** 摘要。 */
    public String getSummary() {
        return summary;
    }

    /** 写入摘要。 */
    public void setSummary(String summary) {
        this.summary = summary;
    }
}
