package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import org.apache.ibatis.annotations.Param;

/**
 * attachment 的访问。登记用 BaseMapper 插入。按标识查找在 XML 里。
 */
public interface AttachmentMapper extends BaseMapper<Attachment> {

    /**
     * 写引用前确认附件还在。没有时返回 null。
     */
    Attachment findByAttachmentId(@Param("attachmentId") String attachmentId);
}
