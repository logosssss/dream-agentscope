package com.zhu.access.infrastructure.persistence.entity.attachment;

/**
 * 按 MESSAGE 引用查出的附件摘要行。仅供历史回填，不进目录 API。
 */
public class AttachmentMessageLink {

    private String refId;

    private String attachmentId;

    private String fileName;

    private String mimeType;

    /** MESSAGE 的 ref_id（sessionId:seq）。 */
    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
