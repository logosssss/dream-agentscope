package com.zhu.audit.api;

import java.util.List;

/**
 * 管理进程登记和查看模型配置。不解析参数 JSON。
 */
public interface ModelConfigCatalog {

    /**
     * 新增 1.0.0。同一标识再登记这个版本是冲突。
     */
    ModelConfigSummary register(String configId, String model, String provider, String tenantScope);

    /**
     * 最近登记的模型配置，含停用。条数默认由调用方给出，实现侧再压到上限。
     * 不读参数 JSON。
     */
    List<ModelConfigSummary> recent(int limit);
}
