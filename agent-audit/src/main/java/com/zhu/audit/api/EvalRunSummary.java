package com.zhu.audit.api;

/**
 * 一次已登记的评测执行。打分前通过数是 0，打分后带实际上过的条数。
 *
 * @param evalRunId 执行标识
 * @param datasetId 数据集标识
 * @param version   这次固定 1.0.0
 * @param totalCases 开始时的用例条数
 * @param passedCases 通过条数
 * @param status    RUNNING 或 SUCCEEDED
 */
public record EvalRunSummary(
        String evalRunId,
        String datasetId,
        String version,
        int totalCases,
        int passedCases,
        String status) {
}
