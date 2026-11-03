package com.zhu.access.api.dto;

import java.util.List;

/**
 * 一次运行的详情。摘要和列表同一套短字段，另带开始和结束时间。步骤不含参数或全文。
 *
 * @param run        已经结束的运行摘要
 * @param steps      按序号排列的步骤，没有时是空列表
 * @param startedAt  开始时间，UTC，没有时区后缀；没有时为空串
 * @param finishedAt 结束时间，UTC，没有时区后缀；没有时为空串
 */
public record RunDetail(RunSummary run, List<RunStepSummary> steps, String startedAt, String finishedAt) {
}
