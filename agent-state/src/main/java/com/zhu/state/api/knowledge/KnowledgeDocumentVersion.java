package com.zhu.state.api.knowledge;

import java.time.LocalDateTime;

/**
 * 本人某文档最新一版的元数据。不含正文，给详情接口用。
 *
 * @param docId     文档业务主键
 * @param version   最新版本号
 * @param kbId      所属知识库
 * @param agentId   绑定 Agent
 * @param title     标题
 * @param status    PENDING / PROCESSING / SUCCESS / FAILED / ARCHIVED
 * @param errorMsg  失败摘要，没有时为空
 * @param createdAt 这一版的创建时间
 * @param updatedAt 这一版的更新时间
 */
public record KnowledgeDocumentVersion(
        String docId,
        int version,
        String kbId,
        String agentId,
        String title,
        String status,
        String errorMsg,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
