package com.zhu.semantic.config;

import com.zhu.semantic.config.permission.PermissionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 产品语义的配置入口。只打开权限规则绑定，不在这里创建执行组件。
 */
@Configuration
@EnableConfigurationProperties(PermissionProperties.class)
public class SemanticConfig {
}