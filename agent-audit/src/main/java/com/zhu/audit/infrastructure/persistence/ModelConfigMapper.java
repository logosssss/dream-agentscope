package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * model_config 的访问。登记用 BaseMapper 插入，列表和对话选模型在 XML 里。
 */
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 启用中的 1.0.0。租户范围优先于全局。没有时返回 null。
     */
    ModelConfig findEnabled(@Param("tenantId") String tenantId);

    /**
     * 最近登记的模型配置。调用方已经把条数收进 1 到 100。含停用。
     */
    List<ModelConfig> recent(@Param("limit") int limit);

    /**
     * 按配置标识和版本取一行，含停用。
     */
    ModelConfig findByConfigId(@Param("configId") String configId, @Param("version") String version);

    /**
     * 只把仍启用的指定版本改成停用。返回影响行数。
     */
    int disableEnabled(@Param("configId") String configId, @Param("version") String version);

    /**
     * 只把停用的指定版本改回启用。返回影响行数。
     */
    int restoreDisabled(@Param("configId") String configId, @Param("version") String version);
}
