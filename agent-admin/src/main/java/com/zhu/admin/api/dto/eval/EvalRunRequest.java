package com.zhu.admin.api.dto.eval;

/**
 * 登记一次评测执行。只带数据集标识，不带模型，也不带通过率。
 *
 * @param datasetId 数据集标识
 */
public record EvalRunRequest(String datasetId) {
}
