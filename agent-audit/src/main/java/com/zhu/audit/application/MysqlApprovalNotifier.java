package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalNotice;
import com.zhu.audit.api.ApprovalNotifier;
import com.zhu.audit.infrastructure.persistence.ApprovalNotificationMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalNotification;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把待确认记成一条未发送通知，并按接收人列出。只在存储开关打开时创建。不调用外部通道。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlApprovalNotifier implements ApprovalNotifier {

    private static final String WEB = "WEB";

    private static final String PENDING = "PENDING";

    private static final int APPROVAL_LIMIT = 64;

    private static final int RECEIVER_LIMIT = 128;

    private final ApprovalNotificationMapper notifications;

    /** 插入和列表用同一个 Mapper。失败原样抛出，由订阅方决定是否影响这次询问。 */
    public MysqlApprovalNotifier(ApprovalNotificationMapper notifications) {
        this.notifications = notifications;
    }

    /**
     * 审批单或接收人为空时不写。超长字段截到列宽，避免通知把已经记下的审批弄失败。
     */
    @Override
    public void pending(String approvalId, String receiverId) {
        if (approvalId == null || approvalId.isBlank() || receiverId == null || receiverId.isBlank()) {
            return;
        }
        ApprovalNotification row = new ApprovalNotification();
        row.setApprovalId(clip(approvalId, APPROVAL_LIMIT));
        row.setReceiverId(clip(receiverId, RECEIVER_LIMIT));
        row.setChannel(WEB);
        row.setSendStatus(PENDING);
        row.setRetryCount(0);
        notifications.insert(row);
    }

    /**
     * 空接收人拒绝。表没有租户列，只按接收人切开。
     * 已发送或失败的行也返回，仍不改状态、不发消息。
     */
    @Override
    public List<ApprovalNotice> owned(String receiverId, int limit) {
        if (receiverId == null || receiverId.isBlank() || receiverId.length() > RECEIVER_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "notification is invalid");
        }
        List<ApprovalNotification> rows =
                notifications.listOwned(receiverId, AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlApprovalNotifier::notice).toList();
    }

    /** 列表不带回错误和发送时间，这次本来就不写这两列。 */
    private static ApprovalNotice notice(ApprovalNotification row) {
        return new ApprovalNotice(
                row.getId(),
                row.getApprovalId(),
                row.getReceiverId(),
                row.getChannel(),
                row.getSendStatus());
    }

    /** 列宽不够时只保留前一段。 */
    private static String clip(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
