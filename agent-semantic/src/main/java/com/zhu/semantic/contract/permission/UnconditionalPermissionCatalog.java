package com.zhu.semantic.contract.permission;

import java.util.List;

/**
 * 契约层：列出可注入 {@code PermissionContextState} 的无条件规则。
 * 运行层 build / 排空重建时读取；带租户、角色或 conditions 的行不出现在这里。
 */
public interface UnconditionalPermissionCatalog {

    /**
     * 仍启用、租户与角色均为 {@code *}、且无条件正文的规则。
     * 空列表表示引擎侧只靠 mode（BYPASS）处理未匹配。
     */
    List<UnconditionalToolRule> list();
}
