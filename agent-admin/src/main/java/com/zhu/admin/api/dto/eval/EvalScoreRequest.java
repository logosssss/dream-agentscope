package com.zhu.admin.api.dto.eval;

/**
 * 控制台给一次评测执行打分。不带模型输出，也不带逐条结果。
 *
 * @param passedCases 通过条数，不能超过开始时记下的用例数
 */
public record EvalScoreRequest(Integer passedCases) {
}
