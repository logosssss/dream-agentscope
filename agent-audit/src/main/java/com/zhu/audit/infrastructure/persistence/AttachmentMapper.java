package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * attachment 的访问。登记用 BaseMapper 插入。按标识查找、列表和详情在 XML 里。
 */
public interface AttachmentMapper extends BaseMapper<Attachment> {

    /**
     * 写引用前确认附件还在。没有时返回 null。
     */
    Attachment findByAttachmentId(@Param("attachmentId") String attachmentId);

    /**
     * 本人挂到该引用上的存储路径。没有行返回空列表。
     */
    List<String> listStoragePathsByRef(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("refType") String refType,
            @Param("refId") String refId);

    /**
     * 按标识取下载所需字段。没有时返回 null。
     */
    Attachment findStored(@Param("attachmentId") String attachmentId);

    /**
     * 本人本租户仍启用的附件，新的在前。条数由调用方截好。
     */
    List<Attachment> listOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("limit") int limit);

    /**
     * 本人本租户仍启用的一条附件。别人的或停用返回 null。
     */
    Attachment findOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("attachmentId") String attachmentId);
}
