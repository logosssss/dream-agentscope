package com.zhu.audit.api;

/**
 * 登记模型配置。不接到运行层，也不解析参数 JSON。
 */
public interface ModelConfigCatalog {

    /**
     * 新增 1.0.0。同一标识再登记这个版本是冲突。
     */
    ModelConfigSummary register(String configId, String model, String provider, String tenantScope);
}
