package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataDeletionRequest;

/**
 * data_deletion_request 的访问。登记用 BaseMapper 插入，不在这里执行删除。
 */
public interface DataDeletionRequestMapper extends BaseMapper<DataDeletionRequest> {
}
