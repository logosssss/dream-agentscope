package com.zhu.audit.api;

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
}
