package com.zhu.docs.api;

import java.time.LocalDateTime;

/**
 * 一次同步的计数。状态是 {@code RUNNING}、{@code SUCCESS}、{@code PARTIAL}、{@code FAILED} 或 {@code NONE}。
 *
 * @param id         运行 id，还没有记录时是 0
 * @param startedAt  开始时间
 * @param finishedAt 结束时间，进行中是 null
 * @param scanned    扫到的文件数
 * @param added      新写入的篇数
 * @param updated    内容或向量有变化的篇数
 * @param removed    源里已经没有、因而标删的篇数
 * @param status     状态
 * @param note       失败或补向量的摘要，不含正文
 */
public record DocsSyncStatus(
        long id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int scanned,
        int added,
        int updated,
        int removed,
        String status,
        String note) {

    /** 还没同步过。前端可以显示空状态，而不是当成错误。 */
    public static DocsSyncStatus none() {
        return new DocsSyncStatus(0, null, null, 0, 0, 0, 0, "NONE", null);
    }
}
