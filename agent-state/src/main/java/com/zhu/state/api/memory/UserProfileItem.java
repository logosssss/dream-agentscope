package com.zhu.state.api.memory;

/**
 * 一行用户画像。给运行层注入短画像用。
 *
 * @param tenantId           租户，可能是空串
 * @param userId             用户
 * @param relationshipStage  关系阶段
 * @param notes              短备注，可能为空
 */
public record UserProfileItem(
        String tenantId,
        String userId,
        String relationshipStage,
        String notes) {
}
