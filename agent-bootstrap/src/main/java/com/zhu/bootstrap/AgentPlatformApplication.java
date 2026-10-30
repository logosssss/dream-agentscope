package com.zhu.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动入口。只扫描 com.zhu 下各模块的配置，自身不写业务。
 */
@SpringBootApplication(scanBasePackages = "com.zhu")
public class AgentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
