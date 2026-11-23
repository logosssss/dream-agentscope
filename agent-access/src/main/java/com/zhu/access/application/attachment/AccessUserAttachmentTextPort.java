package com.zhu.access.application.attachment;

import com.zhu.access.api.dto.attachment.AttachmentContent;
import com.zhu.common.exception.BusinessException;
import com.zhu.semantic.api.attachment.UserAttachmentTextPort;
import org.springframework.stereotype.Service;

/**
 * 接入层：{@link UserAttachmentTextPort} 实现。鉴权与下载走 AttachmentAppService，再抽文本。
 */
@Service
public class AccessUserAttachmentTextPort implements UserAttachmentTextPort {

    private final AttachmentAppService uploads;

    public AccessUserAttachmentTextPort(AttachmentAppService uploads) {
        this.uploads = uploads;
    }

    /**
     * 下载失败时返回说明，不把 BusinessException 抛给模型工具线程。
     */
    @Override
    public String readText(String tenantId, String userId, String attachmentId) {
        if (userId == null || userId.isBlank()) {
            return "未登录，无法读取附件。";
        }
        if (attachmentId == null || attachmentId.isBlank()) {
            return "attachmentId 不能为空。";
        }
        try {
            AttachmentContent content = uploads.download(userId, attachmentId.trim());
            return AttachmentDocumentReader.extract(content.fileName(), content.mimeType(), content.content());
        } catch (BusinessException error) {
            String message = error.getMessage();
            return message == null || message.isBlank() ? "无法读取该附件。" : message;
        } catch (RuntimeException error) {
            return "读取附件失败。";
        }
    }
}
