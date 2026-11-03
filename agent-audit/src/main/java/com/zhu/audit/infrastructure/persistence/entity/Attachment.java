package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * attachment 的一行。只描述元数据，不表示文件已经入库。
 */
@TableName("attachment")
public class Attachment {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 附件标识。 */
    private String attachmentId;

    /** 租户。没有租户时是空串。 */
    private String tenantId;

    /** 所属用户。 */
    private String userId;

    /** 关联会话。可以空。 */
    private String sessionId;

    /** 原始文件名。 */
    private String fileName;

    /** 存储类型。 */
    private String storageType;

    /** 声称的存储路径。 */
    private String storagePath;

    /** MIME。可以空。 */
    private String mimeType;

    /** 声称的大小。 */
    private Long sizeBytes;

    /** 校验和。可以空。 */
    private String checksum;

    /** ACTIVE / DELETED。停用只改状态，不删对象。 */
    private String status;

    /** 登记时间。详情才返回，列表不带。 */
    private LocalDateTime createdAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 附件标识。 */
    public String getAttachmentId() {
        return attachmentId;
    }

    /** 写入附件标识。 */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
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

    /** 会话，可能为空。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。空串应先换成 null。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 文件名。 */
    public String getFileName() {
        return fileName;
    }

    /** 写入文件名。 */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** 存储类型。 */
    public String getStorageType() {
        return storageType;
    }

    /** 写入存储类型。 */
    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    /** 存储路径。 */
    public String getStoragePath() {
        return storagePath;
    }

    /** 写入存储路径。 */
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    /** MIME，可能为空。 */
    public String getMimeType() {
        return mimeType;
    }

    /** 写入 MIME。空串应先换成 null。 */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /** 声称的大小。 */
    public Long getSizeBytes() {
        return sizeBytes;
    }

    /** 写入大小。空值应先换成 0。 */
    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    /** 校验和，可能为空。 */
    public String getChecksum() {
        return checksum;
    }

    /** 写入校验和。空串应先换成 null。 */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。这次只写 ACTIVE。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 登记时间。没有时详情返回空串。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入登记时间。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
