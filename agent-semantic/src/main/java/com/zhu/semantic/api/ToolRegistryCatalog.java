package com.zhu.semantic.api;

import java.util.List;

/**
 * 管理进程登记、停用、恢复和查看工具目录。不执行工具，也不写权限规则。
 */
public interface ToolRegistryCatalog {

    /**
     * 新增 1.0.0。同一标识再登记是冲突。
     */
    ToolRegistrySummary register(String toolId, String name, String module, String description);

    /**
     * 把启用目录改成停用。没有这一行或已停用由实现返回失败。
     */
    ToolRegistrySummary disable(String toolId);

    /**
     * 把停用目录改回启用。没有这一行或已启用由实现返回失败。
     */
    ToolRegistrySummary restore(String toolId);

    /**
     * 最近登记的工具目录，含停用。条数由调用方给出，实现侧再压到上限。
     * 不读 param_schema。
     */
    List<ToolRegistrySummary> recent(int limit);
}
