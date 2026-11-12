package com.zhu.audit.api.eval;

/**
 * 一条已写入的评测用例。不带标签，也不带执行结果。
 *
 * @param datasetId    数据集标识
 * @param version      这次固定 1.0.0
 * @param caseNo       序号，从 1 起
 * @param inputText    输入
 * @param expectedText 期望输出，没有时为空
 */
public record EvalCaseSummary(
        String datasetId,
        String version,
        int caseNo,
        String inputText,
        String expectedText) {
}
