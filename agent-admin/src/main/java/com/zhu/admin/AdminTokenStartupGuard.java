package com.zhu.admin;

import com.zhu.common.security.CallerTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 管理进程启动时校验 Admin 令牌。依赖探测打开时才拦，测试关掉依赖探测可继续用 test-token。
 * 空白或文档占位串直接退出，避免按样例复制后对外裸奔。
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class AdminTokenStartupGuard implements ApplicationRunner {

    private final String token;

    /**
     * 令牌来自 agentscope.admin.token（可用 AGENTSCOPE_ADMIN_TOKEN 覆盖）。
     */
    public AdminTokenStartupGuard(@Value("${agentscope.admin.token:}") String token) {
        this.token = token;
    }

    /** 不通过就抛 IllegalStateException，进程退出。 */
    @Override
    public void run(ApplicationArguments args) {
        CallerTokens.requireSecure("agentscope.admin.token", token);
    }
}
