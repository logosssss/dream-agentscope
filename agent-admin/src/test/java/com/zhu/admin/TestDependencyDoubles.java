package com.zhu.admin;

import com.zhu.admin.api.event.AuditEventRow;
import com.zhu.admin.api.event.AuditQuery;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * 管理进程测试替身。不提供写入记录器，也不连数据库。
 * <p>用 {@code @TestConfiguration} 是为了不被组件扫描捎进每个管理端测试——
 * 需要它的用例显式 {@code @Import} 进来，MySQL 打开时的真实装配才不会被替身盖住。
 * 旧 ApprovalDecider 已硬删。
 */
@TestConfiguration
public class TestDependencyDoubles {

    /** 查询固定为空。 */
    @Bean
    public AuditQuery auditQuery() {
        return limit -> List.<AuditEventRow>of();
    }
}
