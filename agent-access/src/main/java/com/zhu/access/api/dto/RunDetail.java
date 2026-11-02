package com.zhu.access.api.dto;

import java.util.List;

/**
 * 一次运行的详情。摘要和列表同一套短字段，步骤不含参数或全文。
 *
 * @param run   已经结束的运行摘要
 * @param steps 按序号排列的步骤，没有时是空列表
 */
public record RunDetail(RunSummary run, List<RunStepSummary> steps) {
}
