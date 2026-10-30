package com.zhu.semantic.api;

import com.zhu.semantic.contract.PermissionDecision;

/**
 * 工具权限规则。匹配顺序以后由产品域实现，这里只定义结果。
 */
public interface PermissionRuleService {

    /**
     * @param toolId   工具标识
     * @param tenantId 租户，空规则可按全局处理
     * @param roleId   角色
     */
    PermissionDecision decide(String toolId, String tenantId, String roleId);
}
