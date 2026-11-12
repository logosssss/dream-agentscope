package com.zhu.state.api.memory;

/**
 * 遗留用户画像读写缝。每个租户用户一行。
 * 对话主路径不再 upsert；人设中间件仍可读旧行作跨会话触发。
 * 接入层和管理进程不要调用。
 */
public interface UserProfileRepository {

    /**
     * 没有行就插入，有行就覆盖阶段和备注。
     * 阶段只接受约定值。备注不存对话全文。
     */
    void upsert(String tenantId, String userId, String relationshipStage, String notes);

    /**
     * 按租户和用户取一行。没有行时返回 null。
     * 用户为空时不查库。
     */
    UserProfileItem find(String tenantId, String userId);
}
