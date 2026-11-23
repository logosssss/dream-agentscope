package com.zhu.readmodel.entity.attachment;

/**
 * 读模型层的附件摘要行。按 MESSAGE 引用查出，仅供历史回填，不进目录 API。
 * 表映射留在读模型，接入层只消费查询结果。
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

    /** 写入 MESSAGE 引用。空串表示这条摘要还没挂到气泡。 */
    public void setRefId(String refId) {
        this.refId = refId;
    }

    /** 附件标识。 */
    public String getAttachmentId() {
        return attachmentId;
    }

    /** 写入附件标识。 */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /** 展示用文件名。 */
    public String getFileName() {
        return fileName;
    }

    /** 写入展示用文件名。 */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** 文件类型。历史气泡只用来区分图片和文档。 */
    public String getMimeType() {
        return mimeType;
    }

    /** 写入文件类型。 */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
