package com.zhu.bootstrap;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.event.AuditEventRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.audit.api.event.AuditRecorder;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户进程测试替身。MySQL 和 Redis 在测试里关闭，这些接口不能再落到进程内存实现。
 * 旧 ApprovalRecorder / ApprovalDecider 已硬删，不再提供替身。
 */
@Configuration
public class TestDependencyDoubles {

    /** 额度测试用的计数。 */
    @Bean
    public AccessCache accessCache() {
        return new FakeAccessCache();
    }

    /** 审计写入直接丢掉。 */
    @Bean
    public AuditRecorder auditRecorder() {
        return (action, actorId, detail) -> {
        };
    }

    /** 查询固定为空，不连数据库。 */
    @Bean
    public AuditQuery auditQuery() {
        return limit -> List.<AuditEventRow>of();
    }
}
