package com.zhu.state.api.knowledge;

/**
 * 文档某一版本的摘要。不含超长正文时也可带 content 供索引用。
 *
 * @param docId    文档业务主键
 * @param version  版本号，从 1 起
 * @param kbId     所属知识库
 * @param tenantId 租户
 * @param userId   所有者
 * @param agentId  绑定 Agent
 * @param title    标题
 * @param content  正文；列表场景可空
 * @param status   PENDING / PROCESSING / SUCCESS / FAILED / ARCHIVED
 * @param errorMsg 失败摘要
 */
public record KnowledgeDocumentItem(
        String docId,
        int version,
        String kbId,
        String tenantId,
        String userId,
        String agentId,
        String title,
        String content,
        String status,
        String errorMsg) {
}
