package com.zhu.semantic.config;

import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.contract.PermissionRuleService;
import org.springframework.stereotype.Service;

/**
 * 产品域还没提供规则时的默认权限。保证进程能启动，同时不把所有工具都拦成 ASK。
 * 容器里只能有一个 PermissionRuleService。产品域接上规则后替换这个类，不要再注册第二个。
 */
@Service
public class AllowByDefaultPermissionService implements PermissionRuleService {

    /**
     * 没有产品规则就放行。
     */
    @Override
    public PermissionDecision decide(String toolId, String tenantId, String roleId) {
        return PermissionDecision.ALLOW;
    }
}
