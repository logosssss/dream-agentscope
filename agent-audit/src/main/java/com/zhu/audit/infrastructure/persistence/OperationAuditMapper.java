package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.OperationAudit;

/**
 * operation_audit 的访问。登记用 BaseMapper 插入，不在这里改业务表。
 */
public interface OperationAuditMapper extends BaseMapper<OperationAudit> {
}
