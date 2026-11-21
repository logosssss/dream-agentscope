package com.zhu.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动入口。只扫描 com.zhu 下各模块的配置，自身不写业务。
 * 打开异步，供知识库索引离开 HTTP 线程；打开调度，供删除执行器与保留策略清理挂上定时。
 */
@SpringBootApplication(scanBasePackages = "com.zhu")
@EnableAsync
@EnableScheduling
public class AgentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
