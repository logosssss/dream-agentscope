package com.zhu.access.application.auth.gate;

import com.zhu.audit.api.tenant.TenantGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL 关闭时的租户检查。不访问数据库，避免测试为了对话去准备租户行。
 * 和 MySQL 实现互斥，不要两个一起注册。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class OpenTenantGate implements TenantGate {

    /** 存储没开时不拒绝。正式进程打开 MySQL 后改走目录。 */
    @Override
    public void requireActive(String tenantId) {
    }
}
