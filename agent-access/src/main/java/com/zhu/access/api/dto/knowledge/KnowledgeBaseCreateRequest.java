package com.zhu.access.api.dto.knowledge;

/**
 * 显式建库入参。
 *
 * @param name    展示名，必填
 * @param agentId 绑定 Agent，空则 companion-v1
 */
public record KnowledgeBaseCreateRequest(String name, String agentId) {
}
