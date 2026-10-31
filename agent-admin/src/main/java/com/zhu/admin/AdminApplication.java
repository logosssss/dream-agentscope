package com.zhu.admin;

import com.zhu.audit.application.MysqlBillingRollup;
import com.zhu.audit.application.MysqlModelPriceCatalog;
import com.zhu.audit.application.MybatisApprovalDecider;
import com.zhu.audit.application.MybatisAuditQuery;
import com.zhu.audit.config.AuditMapperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 管理进程入口。只扫 com.zhu.admin。
 * 审计只引入查询和决定实现，不扫描写入用的 Recorder，避免在 8100 上再挂一套记录器。
 */
@SpringBootApplication(scanBasePackages = "com.zhu.admin")
@Import({
        MybatisAuditQuery.class,
        MybatisApprovalDecider.class,
        MysqlBillingRollup.class,
        MysqlModelPriceCatalog.class,
        AuditMapperConfig.class
})
public class AdminApplication {

    /** 独立进程启动。不要把这个类交给用户进程的组件扫描。 */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
