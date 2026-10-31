package com.zhu.audit.api;

/**
 * 记下一条还没发出的审批通知。MySQL 关闭时没有这个实现。
 * 不发送消息，也不把状态改成已发送。
 */
public interface ApprovalNotifier {

    /**
     * 给发起人记一条 WEB、PENDING。接收人为空时不写。写失败由调用方吞掉。
     */
    void pending(String approvalId, String receiverId);
}
