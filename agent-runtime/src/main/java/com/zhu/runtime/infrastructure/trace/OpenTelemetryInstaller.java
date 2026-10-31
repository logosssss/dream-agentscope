package com.zhu.runtime.infrastructure.trace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 用户进程启动时安装追踪导出。管理进程不扫描运行层，不会创建这个 Bean。
 */
@Component
public class OpenTelemetryInstaller {

    /**
     * 在 Web 端口打开前安装。地址为空时 {@link OpenTelemetrySetup#install} 直接返回。
     */
    public OpenTelemetryInstaller(
            @Value("${agentscope.otel.endpoint:}") String endpoint,
            @Value("${agentscope.otel.service-name:agentscope-user}") String serviceName) {
        OpenTelemetrySetup.install(endpoint, serviceName);
    }
}
