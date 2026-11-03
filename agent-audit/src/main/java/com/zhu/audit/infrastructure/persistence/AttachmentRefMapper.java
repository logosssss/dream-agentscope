package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.AttachmentRef;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * attachment_ref 的访问。登记用 BaseMapper 插入，按附件列引用。不在这里核对话或运行。
 */
public interface AttachmentRefMapper extends BaseMapper<AttachmentRef> {

    /** 这个附件上的引用，新的在前。 */
    List<AttachmentRef> listByAttachment(
            @Param("attachmentId") String attachmentId, @Param("limit") int limit);
}
