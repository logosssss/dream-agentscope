package com.zhu.semantic.contract.permission;

/**
 * 契约层：一条可注入框架 PermissionEngine 的无条件规则快照。
 * 仅工具名 + 决定；租户/角色已是通配，且无 conditions。
 */
public record UnconditionalToolRule(String toolId, PermissionDecision decision) {
}
