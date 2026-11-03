package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalNotification;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * approval_notification 的访问。追加用 BaseMapper 插入。本人列表在 XML 里，不改发送状态。
 */
public interface ApprovalNotificationMapper extends BaseMapper<ApprovalNotification> {

    /**
     * 本人最近的通知，新的在前。条数由调用方截好。
     */
    List<ApprovalNotification> listOwned(@Param("receiverId") String receiverId, @Param("limit") int limit);

    /**
     * 这个接收人的一条通知。别人的或没有时返回 null。含已发送或失败的行。
     */
    ApprovalNotification findOwned(@Param("id") Long id, @Param("receiverId") String receiverId);
}
