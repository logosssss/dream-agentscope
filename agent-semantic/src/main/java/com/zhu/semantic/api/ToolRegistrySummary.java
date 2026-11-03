package com.zhu.semantic.api;

/**
 * 一条已登记的工具目录。不带 param_schema，也不表示已有代码实现。
 *
 * @param toolId      工具标识，须与 @Tool name 一致
 * @param name        展示名
 * @param version     这次固定 1.0.0
 * @param module      所属模块，没有时为空串
 * @param description 给模型看的说明，没有时为空串
 * @param enabled     是否启用；停用后运行层 listEnabled 不再返回
 */
public record ToolRegistrySummary(
        String toolId,
        String name,
        String version,
        String module,
        String description,
        boolean enabled) {
}
