package com.zhu.admin.api.dto;

/**
 * 控制台登记的工具目录。版本由服务端固定，不从这里传入。
 *
 * @param toolId      工具标识
 * @param name        展示名
 * @param module      所属模块，空白可空
 * @param description 给模型看的说明，空白可空
 */
public record ToolRegistryRequest(String toolId, String name, String module, String description) {
}
