package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * attachment_ref 的一行。只描述关联，不表示文件已经入库。
 */
@TableName("attachment_ref")
public class AttachmentRef {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 已登记的附件。 */
    private String attachmentId;

    /** MESSAGE / RUN / DOCUMENT。 */
    private String refType;

    /** 消息或运行标识。 */
    private String refId;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 附件标识。 */
    public String getAttachmentId() {
        return attachmentId;
    }

    /** 写入附件标识。 */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /** 引用类型。 */
    public String getRefType() {
        return refType;
    }

    /** 写入引用类型。 */
    public void setRefType(String refType) {
        this.refType = refType;
    }

    /** 被引用的标识。 */
    public String getRefId() {
        return refId;
    }

    /** 写入被引用的标识。 */
    public void setRefId(String refId) {
        this.refId = refId;
    }
}
