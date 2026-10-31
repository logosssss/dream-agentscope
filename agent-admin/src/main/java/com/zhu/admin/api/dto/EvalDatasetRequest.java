package com.zhu.admin.api.dto;

/**
 * 控制台登记的评测数据集。版本由服务端固定，不从这里传入。
 *
 * @param datasetId   数据集标识
 * @param name        名称
 * @param description 短说明，可以空
 */
public record EvalDatasetRequest(String datasetId, String name, String description) {
}
