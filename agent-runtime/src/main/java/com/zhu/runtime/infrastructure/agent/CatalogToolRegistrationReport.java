package com.zhu.runtime.infrastructure.agent;

import java.util.List;

/**
 * 目录工具一次注册的结果报告。基础设施装配层：供日志与单测观察，不驱动网关成败。
 *
 * @param registered 成功挂上的 toolId（顺序与尝试顺序一致）
 * @param failed     注册抛错而跳过的 toolId
 */
public record CatalogToolRegistrationReport(List<String> registered, List<String> failed) {

    /** 空目录或无候选时的空报告。 */
    public static CatalogToolRegistrationReport empty() {
        return new CatalogToolRegistrationReport(List.of(), List.of());
    }

    /**
     * 防御性拷贝，避免调用方改动内部列表。
     */
    public CatalogToolRegistrationReport {
        registered = registered == null ? List.of() : List.copyOf(registered);
        failed = failed == null ? List.of() : List.copyOf(failed);
    }
}
