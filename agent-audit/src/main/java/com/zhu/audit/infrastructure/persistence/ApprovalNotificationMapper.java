package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalNotification;

/**
 * approval_notification 的访问。追加用 BaseMapper 插入，不在这里查询或改状态。
 */
public interface ApprovalNotificationMapper extends BaseMapper<ApprovalNotification> {
}
