package com.zhu.audit.api.eval;

/**
 * 给已登记的评测数据集追加用例。不执行，也不改数据集版本。
 */
public interface EvalCaseCatalog {

    /**
     * 追加到 1.0.0。数据集不存在时拒绝。同一序号再写一次是冲突。
     */
    EvalCaseSummary add(String datasetId, Integer caseNo, String inputText, String expectedText);
}
