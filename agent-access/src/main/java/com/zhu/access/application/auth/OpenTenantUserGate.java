package com.zhu.access.application.auth;

import com.zhu.audit.api.TenantUserGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL 关闭时的租户用户检查。不访问数据库，避免测试为了对话去准备用户行。
 * 和 MySQL 实现互斥，不要两个一起注册。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class OpenTenantUserGate implements TenantUserGate {

    /** 存储没开时不拒绝。正式进程打开 MySQL 后改走目录。 */
    @Override
    public void requireActive(String tenantId, String userId) {
    }
}
