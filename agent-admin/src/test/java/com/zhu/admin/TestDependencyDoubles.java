package com.zhu.admin;

import com.zhu.admin.api.event.AuditEventRow;
import com.zhu.admin.api.event.AuditQuery;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理进程测试替身。不提供写入记录器，也不连数据库。
 * 旧 ApprovalDecider 已硬删。
 */
@Configuration
public class TestDependencyDoubles {

    /** 查询固定为空。 */
    @Bean
    public AuditQuery auditQuery() {
        return limit -> List.<AuditEventRow>of();
    }
}
