package com.zhu.audit.infrastructure.persistence.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.event.OperationAudit;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * operation_audit 的访问。登记用 BaseMapper 插入。最近列表在 XML 里。
 */
public interface OperationAuditMapper extends BaseMapper<OperationAudit> {

    /**
     * 最近的操作。按创建时间倒序。
     */
    List<OperationAudit> recent(@Param("limit") int limit);
}
