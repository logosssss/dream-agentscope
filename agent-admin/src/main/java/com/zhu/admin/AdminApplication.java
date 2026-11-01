package com.zhu.admin;

import com.zhu.audit.application.MysqlBillingRollup;
import com.zhu.audit.application.MysqlEvalCaseCatalog;
import com.zhu.audit.application.MysqlEvalRunCatalog;
import com.zhu.audit.application.MysqlEvalDatasetCatalog;
import com.zhu.audit.application.MysqlModelConfigCatalog;
import com.zhu.audit.application.MysqlDeletionRequestDecider;
import com.zhu.audit.application.MysqlOperationAuditCatalog;
import com.zhu.audit.application.MysqlSafetyInterventionCatalog;
import com.zhu.audit.application.MysqlRetentionPolicyCatalog;
import com.zhu.audit.application.MysqlTaskScheduleCatalog;
import com.zhu.audit.application.MysqlModelPriceCatalog;
import com.zhu.audit.application.MysqlQuotaUsageQuery;
import com.zhu.audit.application.MysqlTenantDirectory;
import com.zhu.audit.application.MysqlTenantQuota;
import com.zhu.audit.application.MybatisApprovalDecider;
import com.zhu.audit.application.MybatisAuditQuery;
import com.zhu.audit.application.MysqlUserTodoCatalog;
import com.zhu.audit.config.AuditMapperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 管理进程入口。只扫 com.zhu.admin。
 * 审计只引入查询、决定和关待办，不扫描写入用的 Recorder，避免在 8100 上再挂一套记录器。
 * 安全干预只引入目录，不引入 SafetyInterventionRecorder。
 */
@SpringBootApplication(scanBasePackages = "com.zhu.admin")
@Import({
        MybatisAuditQuery.class,
        MybatisApprovalDecider.class,
        MysqlUserTodoCatalog.class,
        MysqlBillingRollup.class,
        MysqlModelPriceCatalog.class,
        MysqlModelConfigCatalog.class,
        MysqlRetentionPolicyCatalog.class,
        MysqlTaskScheduleCatalog.class,
        MysqlOperationAuditCatalog.class,
        MysqlSafetyInterventionCatalog.class,
        MysqlDeletionRequestDecider.class,
        MysqlTenantDirectory.class,
        MysqlTenantQuota.class,
        MysqlQuotaUsageQuery.class,
        MysqlEvalDatasetCatalog.class,
        MysqlEvalCaseCatalog.class,
        MysqlEvalRunCatalog.class,
        AuditMapperConfig.class
})
public class AdminApplication {

    /** 独立进程启动。不要把这个类交给用户进程的组件扫描。 */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
