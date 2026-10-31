package com.zhu.audit.api;

/**
 * 登记评测数据集。不写用例，也不发起评测。
 */
public interface EvalDatasetCatalog {

    /**
     * 新增 1.0.0。同一标识和版本再登记一次是冲突。说明可以空。
     */
    EvalDatasetSummary register(String datasetId, String name, String description);
}
