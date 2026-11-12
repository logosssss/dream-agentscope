package com.zhu.admin;

import com.zhu.audit.application.billing.MysqlBillingRollup;
import com.zhu.audit.application.eval.MysqlEvalCaseCatalog;
import com.zhu.audit.application.eval.MysqlEvalRunCatalog;
import com.zhu.audit.application.eval.MysqlEvalDatasetCatalog;
import com.zhu.audit.application.model.MysqlModelConfigCatalog;
import com.zhu.audit.application.deletion.MysqlDeletionRequestDecider;
import com.zhu.audit.application.event.MysqlOperationAuditCatalog;
import com.zhu.audit.application.safety.MysqlSafetyInterventionCatalog;
import com.zhu.audit.application.retention.MysqlRetentionPolicyCatalog;
import com.zhu.audit.application.schedule.MysqlTaskScheduleCatalog;
import com.zhu.audit.application.billing.MysqlModelPriceCatalog;
import com.zhu.audit.application.quota.MysqlQuotaUsageQuery;
import com.zhu.audit.application.tenant.MysqlTenantDirectory;
import com.zhu.audit.application.quota.MysqlTenantQuota;
import com.zhu.audit.application.tenant.MysqlTenantUserCatalog;
import com.zhu.audit.application.approval.MybatisApprovalDecider;
import com.zhu.audit.application.event.MybatisAuditQuery;
import com.zhu.audit.application.approval.MysqlUserTodoCatalog;
import com.zhu.audit.config.AuditMapperConfig;
import com.zhu.semantic.application.MysqlToolPermissionCatalog;
import com.zhu.semantic.application.MysqlToolRegistryCatalog;
import com.zhu.semantic.config.PermissionMapperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 管理进程入口。只扫 com.zhu.admin。
 * 审计只引入查询、决定和关待办，不扫描写入用的 Recorder，避免在 8100 上再挂一套记录器。
 * 安全干预只引入目录，不引入 SafetyInterventionRecorder。
 * 工具目录只引入登记实现与语义 Mapper，不引入 StoredToolCatalog。
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
        MysqlTenantUserCatalog.class,
        MysqlTenantQuota.class,
        MysqlQuotaUsageQuery.class,
        MysqlEvalDatasetCatalog.class,
        MysqlEvalCaseCatalog.class,
        MysqlEvalRunCatalog.class,
        MysqlToolRegistryCatalog.class,
        MysqlToolPermissionCatalog.class,
        AuditMapperConfig.class,
        PermissionMapperConfig.class
})
public class AdminApplication {

    /** 独立进程启动。不要把这个类交给用户进程的组件扫描。 */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
