package com.zhu.semantic.api;

import com.zhu.semantic.contract.PermissionDecision;

public interface PermissionRuleService {

    PermissionDecision decide(String toolId, String tenantId, String roleId);
}
