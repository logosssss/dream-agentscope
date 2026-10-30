package com.zhu.semantic.contract;

/**
 * 工具目录里的一条工具。运行层只消费这个描述，不认识具体业务工具。
 *
 * @param toolId      稳定的工具标识
 * @param name        展示名
 * @param description 给模型看的说明
 */
public record ToolDescriptor(String toolId, String name, String description) {
}
