package com.zhu.audit.infrastructure.persistence.entity.approval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * approval_notification 的一行。只表示还没推送，不表示消息已经送到。
 */
@TableName("approval_notification")
public class ApprovalNotification {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 审批单标识。 */
    private String approvalId;

    /** 接收人，取发起用户。 */
    private String receiverId;

    /** WEB。这次不写 IM、邮件或短信。 */
    private String channel;

    /** PENDING。这次不改成 SENT 或 FAILED。 */
    private String sendStatus;

    /** 固定为 0。不重试。 */
    private Integer retryCount;

    /** 创建时间。详情才读，列表不带。 */
    private LocalDateTime createdAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 审批单标识。 */
    public String getApprovalId() {
        return approvalId;
    }

    /** 写入审批单标识。 */
    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    /** 接收人。 */
    public String getReceiverId() {
        return receiverId;
    }

    /** 写入接收人。 */
    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    /** 通道。 */
    public String getChannel() {
        return channel;
    }

    /** 写入通道。 */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /** 发送状态。 */
    public String getSendStatus() {
        return sendStatus;
    }

    /** 写入发送状态。 */
    public void setSendStatus(String sendStatus) {
        this.sendStatus = sendStatus;
    }

    /** 重试次数。 */
    public Integer getRetryCount() {
        return retryCount;
    }

    /** 写入重试次数。 */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /** 创建时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入创建时间。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
