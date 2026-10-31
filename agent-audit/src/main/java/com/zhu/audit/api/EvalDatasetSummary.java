package com.zhu.audit.api;

/**
 * 一个已登记的评测数据集。不带用例。
 *
 * @param datasetId   数据集标识
 * @param name        名称
 * @param version     这次固定 1.0.0
 * @param description 短说明，没有时为空
 */
public record EvalDatasetSummary(String datasetId, String name, String version, String description) {
}
