package com.zhu.audit.api.model;

/**
 * 对话前读取启用中的模型配置。MySQL 关闭时没有这个实现，模型用进程配置。
 * 不解析参数 JSON。
 */
public interface ModelConfigLookup {

    /**
     * 返回 provider:model。没有启用行时返回 null。
     * 同一租户优先于全局 *，同级取最新一行。
     */
    String resolveModel(String tenantId);
}
