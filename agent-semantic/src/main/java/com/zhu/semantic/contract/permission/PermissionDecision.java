package com.zhu.semantic.contract.permission;

/**
 * 工具权限的三种决定。规则来自产品域，运行层不自己猜。
 */
public enum PermissionDecision {
    /** 直接允许。 */
    ALLOW,
    /** 先问人再执行。 */
    ASK,
    /** 拒绝。 */
    DENY
}
