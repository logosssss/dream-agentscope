package com.zhu.audit.api.approval;

import java.util.List;

/**
 * 记下并查看还没发出的审批通知。MySQL 关闭时没有这个实现。
 * 不发送消息，也不把状态改成已发送。
 */
public interface ApprovalNotifier {

    /**
     * 给发起人记一条 WEB、PENDING。接收人为空时不写。写失败由调用方吞掉。
     */
    void pending(String approvalId, String receiverId);

    /**
     * 当前接收人最近的通知。默认 50 条，最多 100 条。
     * 表没有租户列，不按租户再滤；也不把别人的行带出来。
     */
    List<ApprovalNotice> owned(String receiverId, int limit);

    /**
     * 当前接收人的一条通知。别人的或没有时按找不到。
     * 含已发送或失败的行，仍不改状态、不发消息，也不带回错误和发送时间。
     */
    ApprovalNoticeDetail ownedOne(String receiverId, Long notificationId);
}
