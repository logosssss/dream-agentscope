package com.zhu.runtime.api.event;

/**
 * 审计写入口。运行层和接入层依赖它，审计实现不能反向依赖这两层。
 */
public interface AuditRecorder {

    /**
     * 记一条操作。
     *
     * @param action  动作名
     * @param actorId 操作者
     * @param detail  补充说明，不放密钥和全文提示词
     */
    void record(String action, String actorId, String detail);

    /**
     * 记一条带归属的操作：会话与这一轮运行。
     * 默认退回三参版本，进程内实现与测试替身不必改；MySQL 实现的表里有这两列，会真正写进去。
     *
     * @param action    动作名
     * @param actorId   操作者
     * @param sessionId 会话，可空
     * @param runId     运行标识，可空（任务类动作没有运行）
     * @param detail    补充说明
     */
    default void record(
            String action, String actorId, String sessionId, String runId, String detail) {
        record(action, actorId, detail);
    }
}
