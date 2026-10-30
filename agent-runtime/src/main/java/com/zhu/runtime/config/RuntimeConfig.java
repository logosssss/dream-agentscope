package com.zhu.runtime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 打开运行配置绑定。这里不创建 HarnessAgent，避免容器里出现 Agent 单例。
 */
@Configuration
@EnableConfigurationProperties(AgentRuntimeProperties.class)
public class RuntimeConfig {
}
