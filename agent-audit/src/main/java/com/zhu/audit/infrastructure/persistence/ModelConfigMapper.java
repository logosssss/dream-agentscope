package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;

/**
 * model_config 的访问。登记用 BaseMapper 插入，不在这里查询对话用的模型。
 */
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {
}
