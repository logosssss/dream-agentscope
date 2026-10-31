package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataRetentionPolicy;

/**
 * data_retention_policy 的访问。登记用 BaseMapper 插入，不在这里执行清理。
 */
public interface DataRetentionPolicyMapper extends BaseMapper<DataRetentionPolicy> {
}
