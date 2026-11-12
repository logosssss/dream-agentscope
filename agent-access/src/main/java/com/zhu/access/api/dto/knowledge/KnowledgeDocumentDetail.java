package com.zhu.access.api.dto.knowledge;

/**
 * 本人本租户一篇文档的最新版。不带正文，也不改索引状态。
 *
 * @param docId     文档业务主键
 * @param version   最新版本号
 * @param kbId      所属知识库
 * @param agentId   绑定 Agent
 * @param title     标题
 * @param status    PENDING / PROCESSING / SUCCESS / FAILED / ARCHIVED
 * @param errorMsg  失败摘要，没有时为空
 * @param createdAt 创建时间，UTC，没有时区后缀；没有时为空串
 * @param updatedAt 更新时间，UTC，没有时区后缀；没有时为空串
 */
public record KnowledgeDocumentDetail(
        String docId,
        int version,
        String kbId,
        String agentId,
        String title,
        String status,
        String errorMsg,
        String createdAt,
        String updatedAt) {
}
