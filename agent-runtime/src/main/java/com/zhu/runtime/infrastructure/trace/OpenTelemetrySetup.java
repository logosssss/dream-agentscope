package com.zhu.runtime.infrastructure.trace;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * 按配置安装 OpenTelemetry。运行层在接请求前调用。地址为空时保持空实现，不连采集器。
 */
public final class OpenTelemetrySetup {

    private static final Logger log = Logger.getLogger(OpenTelemetrySetup.class.getName());

    /** 没有服务名时用这个，避免上报成 unknown_service。 */
    static final String DEFAULT_SERVICE_NAME = "agentscope-user";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private OpenTelemetrySetup() {
    }

    /**
     * 地址为空时返回 false，不启动导出。已经安装过也不再装第二次。
     * 安装失败只记日志，追踪不能挡住对话。
     */
    public static boolean install(String endpoint, String serviceName) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return false;
        }
        try {
            AutoConfiguredOpenTelemetrySdk.builder()
                    .addPropertiesSupplier(() -> exportProperties(endpoint, serviceName))
                    .build();
            return true;
        } catch (RuntimeException ex) {
            INSTALLED.set(false);
            log.warning("otel exporter was not started");
            return false;
        }
    }

    /**
     * 只导出 trace。度量和日志关掉，避免没配的端口也被连上。
     * 地址原样使用调用方传入的值。
     */
    public static Map<String, String> exportProperties(String endpoint, String serviceName) {
        String name = serviceName == null || serviceName.isBlank() ? DEFAULT_SERVICE_NAME : serviceName;
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("otel.service.name", name);
        properties.put("otel.traces.exporter", "otlp");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.logs.exporter", "none");
        properties.put("otel.exporter.otlp.endpoint", endpoint.trim());
        return properties;
    }
}
