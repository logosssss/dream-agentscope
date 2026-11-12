package com.zhu.admin.api.dto.eval;

/**
 * 追加到已登记数据集的一条用例。版本由路径上的数据集决定，固定 1.0.0。
 *
 * @param caseNo       序号，从 1 起
 * @param inputText    输入
 * @param expectedText 期望输出，可以空
 */
public record EvalCaseRequest(Integer caseNo, String inputText, String expectedText) {
}
