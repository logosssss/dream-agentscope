package com.zhu.audit.api;

/**
 * 一次已登记的评测执行。通过数这次保持 0，因为还没有打分。
 *
 * @param evalRunId 执行标识
 * @param datasetId 数据集标识
 * @param version   这次固定 1.0.0
 * @param totalCases 开始时的用例条数
 * @param passedCases 通过条数，这次是 0
 * @param status    这次是 RUNNING
 */
public record EvalRunSummary(
        String evalRunId,
        String datasetId,
        String version,
        int totalCases,
        int passedCases,
        String status) {
}
