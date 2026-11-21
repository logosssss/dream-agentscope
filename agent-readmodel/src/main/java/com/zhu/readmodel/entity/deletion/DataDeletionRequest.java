package com.zhu.readmodel.entity.deletion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * data_deletion_request 的一行。登记时只表示用户提出了删除；
 * 用户进程执行完才写 executedAt / executedNote，并把状态改成 DONE 或 FAILED。
 */
@TableName("data_deletion_request")
public class DataDeletionRequest {

    /** 自增主键。对外用 requestId。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求标识。 */
    private String requestId;

    /** 发起人。 */
    private String userId;

    /** 租户。没有时是空串。 */
    private String tenantId;

    /** SESSION / ATTACHMENT / ALL。 */
    private String dataType;

    /** 这次固定 PENDING。 */
    private String status;

    /** 原因。没有时留空。 */
    private String reason;

    /** 提出时间。详情才读，列表不带。 */
    private LocalDateTime requestedAt;

    /** 执行时间。用户进程真删（或 dry-run 预览）后写入。 */
    private LocalDateTime executedAt;

    /** 执行结果摘要。失败时是异常摘要，正常时是各表计数。 */
    private String executedNote;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 请求标识。 */
    public String getRequestId() {
        return requestId;
    }

    /** 写入请求标识。 */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /** 发起人。 */
    public String getUserId() {
        return userId;
    }

    /** 写入发起人。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 数据类型。 */
    public String getDataType() {
        return dataType;
    }

    /** 写入数据类型。 */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 原因。 */
    public String getReason() {
        return reason;
    }

    /** 写入原因。 */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /** 提出时间。 */
    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    /** 写入提出时间。 */
    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    /** 执行时间。还没执行时为空。 */
    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    /** 写入执行时间。 */
    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    /** 执行结果摘要。还没执行时为空。 */
    public String getExecutedNote() {
        return executedNote;
    }

    /** 写入执行结果摘要。 */
    public void setExecutedNote(String executedNote) {
        this.executedNote = executedNote;
    }
}
