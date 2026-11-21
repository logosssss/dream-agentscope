package com.zhu.access.application.lifecycle;

import java.util.List;
import java.util.Map;

/**
 * 一次删除执行的结果。只写计数与步骤名，不写被删的正文。
 *
 * @param userId  被处理的用户
 * @param dataType SESSION / ATTACHMENT / ALL
 * @param dryRun  是否只统计没真删
 * @param deleted 步骤名 → 行数（dry-run 下是「将会删掉」的行数）
 * @param skipped 被跳过的步骤及原因，例如超过单表上限、对象存储不可用
 */
public record PurgeReport(
        String userId,
        String dataType,
        boolean dryRun,
        Map<String, Integer> deleted,
        List<String> skipped) {

    /** 防御性拷贝：报告生成后不再变化。 */
    public PurgeReport {
        deleted = deleted == null ? Map.of() : Map.copyOf(deleted);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    /** 落库到 executed_note 的一行摘要，长度由调用方再截。 */
    public String summary() {
        StringBuilder text = new StringBuilder();
        if (dryRun) {
            text.append("dry-run ");
        }
        text.append("deleted=").append(deleted.isEmpty() ? "none" : deleted);
        if (!skipped.isEmpty()) {
            text.append(" skipped=").append(skipped);
        }
        return text.toString();
    }
}
