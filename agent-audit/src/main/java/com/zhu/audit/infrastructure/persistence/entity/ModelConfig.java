package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * model_config 的一行。启用的 1.0.0 可以驱动本次对话选模型。
 */
@TableName("model_config")
public class ModelConfig {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置标识。 */
    private String configId;

    /** 模型名。 */
    private String model;

    /** 供应方。 */
    private String provider;

    /** 固定 1.0.0。 */
    private String version;

    /** 租户范围。* 表示全局。 */
    private String tenantScope;

    /** 1 启用。 */
    private Integer enabled;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 配置标识。 */
    public String getConfigId() {
        return configId;
    }

    /** 写入配置标识。 */
    public void setConfigId(String configId) {
        this.configId = configId;
    }

    /** 模型名。 */
    public String getModel() {
        return model;
    }

    /** 写入模型名。 */
    public void setModel(String model) {
        this.model = model;
    }

    /** 供应方。 */
    public String getProvider() {
        return provider;
    }

    /** 写入供应方。 */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /** 版本。 */
    public String getVersion() {
        return version;
    }

    /** 写入版本。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 租户范围。 */
    public String getTenantScope() {
        return tenantScope;
    }

    /** 写入租户范围。 */
    public void setTenantScope(String tenantScope) {
        this.tenantScope = tenantScope;
    }

    /** 是否启用。 */
    public Integer getEnabled() {
        return enabled;
    }

    /** 写入启用标记。 */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
