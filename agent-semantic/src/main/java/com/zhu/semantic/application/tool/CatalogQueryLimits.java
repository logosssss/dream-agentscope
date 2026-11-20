package com.zhu.semantic.application.tool;

/**
 * 管理列表条数上限。避免一次把工具目录整表拉进管理进程。
 * public：permission 包的登记实现也要归一 limit，不能再靠同包可见性。
 */
public final class CatalogQueryLimits {

    private CatalogQueryLimits() {
    }

    /** 未指定时用 50，调用方传得再大也只取 100。 */
    public static int normalize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
