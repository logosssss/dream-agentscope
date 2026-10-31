package com.zhu.audit.api;

/**
 * 登记一次评测执行。不调用模型，也不把状态改成完成。
 */
public interface EvalRunCatalog {

    /**
     * 为 1.0.0 记下一次执行。数据集不存在，或还没有用例时拒绝。
     */
    EvalRunSummary start(String datasetId);
}
