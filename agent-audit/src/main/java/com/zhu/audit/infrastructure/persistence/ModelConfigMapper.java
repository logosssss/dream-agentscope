package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;
import org.apache.ibatis.annotations.Param;

/**
 * model_config 的访问。登记用 BaseMapper 插入，对话选模型在 XML 里。
 */
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 启用中的 1.0.0。租户范围优先于全局。没有时返回 null。
     */
    ModelConfig findEnabled(@Param("tenantId") String tenantId);
}
