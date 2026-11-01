package com.zhu.state.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * user_profile 的一行。覆盖写走 XML，建表仍由 Flyway 负责。
 * 同一租户用户一行。
 */
@TableName("user_profile")
public class UserProfile {

    /** 自增主键。覆盖写时不用它。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户。没有租户时是空串，和唯一键对齐。 */
    private String tenantId;

    /** 画像所属用户。 */
    private String userId;

    /** 关系阶段。只接受约定值。 */
    private String relationshipStage;

    /** 短备注。不存对话全文。空白时留空。 */
    private String notes;

    /** 入库时间。留空时用表上的默认值。 */
    private LocalDateTime createdAt;

    /** 更新时间。由数据库维护。 */
    private LocalDateTime updatedAt;

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

    /** 关系阶段。 */
    public String getRelationshipStage() {
        return relationshipStage;
    }

    /** 写入关系阶段。未登记的值不要写进来。 */
    public void setRelationshipStage(String relationshipStage) {
        this.relationshipStage = relationshipStage;
    }

    /** 短备注，可能为空。 */
    public String getNotes() {
        return notes;
    }

    /** 写入备注。空串应先换成 null。 */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** 入库时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入入库时间。通常留空。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 更新时间。 */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 写入更新时间。通常留空。 */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
