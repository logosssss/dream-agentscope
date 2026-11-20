package com.zhu.admin;

import com.zhu.semantic.application.permission.MysqlToolPermissionCatalog;
import com.zhu.semantic.application.tool.MysqlToolRegistryCatalog;
import com.zhu.semantic.config.permission.PermissionMapperConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 管理进程入口。只扫 com.zhu.admin。
 * 审计/租户/额度等目录实现已迁入本模块，由组件扫描装配；不引入 AuditRecorder / SafetyInterventionRecorder。
 * 工具目录只引入登记实现与语义 Mapper，不引入 StoredToolCatalog。
 */
@SpringBootApplication(scanBasePackages = "com.zhu.admin")
@Import({
        MysqlToolRegistryCatalog.class,
        MysqlToolPermissionCatalog.class,
        PermissionMapperConfig.class
})
public class AdminApplication {

    /** 独立进程启动。不要把这个类交给用户进程的组件扫描。 */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
