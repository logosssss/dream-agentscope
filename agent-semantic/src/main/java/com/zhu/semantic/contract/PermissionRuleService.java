package com.zhu.semantic.contract;

import java.util.Map;

/**
 * 工具权限规则。放在契约包，运行层的审批 Hook 和接入层都能调用，又不碰到产品实现。
 * 匹配顺序由产品域实现，这里只定义结果。
 */
public interface PermissionRuleService {

    /**
     * 给出一次工具调用的决定。
     * 没有更具体的规则时必须返回 ALLOW，不能返回空，否则调用方无法区分“没配置”和“拒绝”。
     *
     * @param toolId   工具标识
     * @param tenantId 租户，空则按全局规则
     * @param roleId   角色，当前请求还没有角色时为空
     */
    PermissionDecision decide(String toolId, String tenantId, String roleId);

    /**
     * 带工具入参判定。默认忽略入参，兼容只看工具名的实现；表条件里的 maxLen / minLen 要覆盖此方法。
     *
     * @param toolId   工具标识
     * @param tenantId 租户，空则按全局规则
     * @param roleId   角色，当前请求还没有角色时为空
     * @param params   本次工具入参，可空
     */
    default PermissionDecision decide(String toolId, String tenantId, String roleId, Map<String, ?> params) {
        return decide(toolId, tenantId, roleId);
    }
}
