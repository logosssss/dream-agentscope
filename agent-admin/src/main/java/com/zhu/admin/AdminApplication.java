package com.zhu.admin;

import com.zhu.ops.quota.MysqlTenantQuota;
import com.zhu.ops.tenant.MysqlTenantDirectory;
import com.zhu.ops.tenant.MysqlTenantUserCatalog;
import com.zhu.readmodel.config.ReadModelMapperConfig;
import com.zhu.semantic.application.permission.MysqlToolPermissionCatalog;
import com.zhu.semantic.application.tool.MysqlToolRegistryCatalog;
import com.zhu.semantic.config.permission.PermissionMapperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 管理进程入口。只扫 com.zhu.admin。
 * 租户、租户用户和额度目录在 ops，扫描范围够不着，必须显式导入。
 * 不引入 AuditRecorder / SafetyInterventionRecorder。
 * 工具目录只引入登记实现与语义 Mapper，不引入 StoredToolCatalog。
 * 共享表的 Mapper 在 com.zhu.readmodel.persistence，不在本进程的扫描范围里，必须显式导入
 * {@link ReadModelMapperConfig}——否则 MySQL 打开时所有依赖 readmodel Mapper 的 Bean 都建不起来。
 */
@SpringBootApplication(scanBasePackages = "com.zhu.admin")
@Import({
        MysqlTenantDirectory.class,
        MysqlTenantUserCatalog.class,
        MysqlTenantQuota.class,
        MysqlToolRegistryCatalog.class,
        MysqlToolPermissionCatalog.class,
        PermissionMapperConfig.class,
        ReadModelMapperConfig.class
})
public class AdminApplication {

    /** 独立进程启动。不要把这个类交给用户进程的组件扫描。 */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
