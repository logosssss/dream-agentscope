package com.zhu.audit.api;

/**
 * 登记和打分一次评测执行。不调用模型，也不写逐条结果。
 */
public interface EvalRunCatalog {

    /**
     * 为 1.0.0 记下一次执行。数据集不存在，或还没有用例时拒绝。
     */
    EvalRunSummary start(String datasetId);

    /**
     * 给仍在 RUNNING 的执行写入通过数和通过率，并改成 SUCCEEDED。
     * 已经打过分再打一次是冲突。不调用模型。
     */
    EvalRunSummary score(String evalRunId, Integer passedCases);
}
