package com.zhu.semantic.application;

/**
 * 管理列表条数上限。避免一次把工具目录整表拉进管理进程。
 */
final class CatalogQueryLimits {

    private CatalogQueryLimits() {
    }

    /** 未指定时用 50，调用方传得再大也只取 100。 */
    static int normalize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
