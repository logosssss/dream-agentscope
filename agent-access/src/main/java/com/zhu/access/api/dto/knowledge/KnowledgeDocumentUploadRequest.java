package com.zhu.access.api.dto.knowledge;

/**
 * 上传或更新文档正文。接口层限制 UTF-8 不超过 2MB。
 *
 * @param kbId    知识库，必填
 * @param docId   更新时带已有 id；新建可空，由服务生成
 * @param title   标题
 * @param content Markdown/纯文本正文
 */
public record KnowledgeDocumentUploadRequest(String kbId, String docId, String title, String content) {
}
