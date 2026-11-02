package com.zhu.access.api.dto;

/**
 * 一次运行里的一步。不带回参数、正文、耗时或 token。
 *
 * @param stepNo   从 1 开始
 * @param stepType REASONING / TOOL / APPROVAL
 * @param toolId   工具名，推理步骤为空
 * @param status   SUCCEEDED / FAILED / SKIPPED
 */
public record RunStepSummary(int stepNo, String stepType, String toolId, String status) {
}
