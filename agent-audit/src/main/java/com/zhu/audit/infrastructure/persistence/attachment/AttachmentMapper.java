package com.zhu.audit.infrastructure.persistence.attachment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.attachment.Attachment;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * attachment 的访问。登记用 BaseMapper 插入。按标识查找、列表、详情与停用恢复在 XML 里。
 */
public interface AttachmentMapper extends BaseMapper<Attachment> {

    /**
     * 写引用前确认附件还在。含停用。没有时返回 null。
     */
    Attachment findByAttachmentId(@Param("attachmentId") String attachmentId);

    /**
     * 本人本租户的一条，含停用。别人的返回 null。
     */
    Attachment findOwnedAny(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("attachmentId") String attachmentId);

    /**
     * 只把仍启用的本人本租户行改成 DELETED。返回影响行数。
     */
    int markDeleted(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("attachmentId") String attachmentId);

    /**
     * 只把已停用的本人本租户行改回 ACTIVE。返回影响行数。
     */
    int markActive(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("attachmentId") String attachmentId);

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
