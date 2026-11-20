package com.zhu.bootstrap;

import com.zhu.cache.api.CacheFacade;
import com.zhu.runtime.api.event.AuditRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户进程测试替身。MySQL 和 Redis 在测试里关闭，这些接口不能再落到进程内存实现。
 * 管理端 AuditQuery 不在用户进程；旧 ApprovalRecorder / ApprovalDecider 已硬删。
 */
@Configuration
public class TestDependencyDoubles {

    /** 额度 / 会话等测试用的缓存门面。 */
    @Bean
    public CacheFacade cacheFacade() {
        return new FakeCacheFacade();
    }

    /** 审计写入直接丢掉。 */
    @Bean
    public AuditRecorder auditRecorder() {
        return (action, actorId, detail) -> {
        };
    }
}
