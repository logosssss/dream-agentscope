package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.trace.OpenTelemetrySetup;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 没配采集器地址时不安装导出，追踪中间件本身也不去连外部。
 */
class OpenTelemetrySetupTest {

    /** 空白地址保持空实现。 */
    @Test
    void blankEndpointDoesNotInstall() {
        assertThat(OpenTelemetrySetup.install(" ", "agentscope-user")).isFalse();
        assertThat(OpenTelemetrySetup.install(null, "agentscope-user")).isFalse();
    }

    /** 导出配置只用传入的地址，度量和日志保持关闭。 */
    @Test
    void exportPropertiesUseGivenEndpoint() {
        assertThat(OpenTelemetrySetup.exportProperties("http://otel.example:4318", "svc"))
                .containsEntry("otel.exporter.otlp.endpoint", "http://otel.example:4318")
                .containsEntry("otel.service.name", "svc")
                .containsEntry("otel.traces.exporter", "otlp")
                .containsEntry("otel.metrics.exporter", "none")
                .containsEntry("otel.logs.exporter", "none");
    }

    /** 中间件可以无地址构造。 */
    @Test
    void middlewareConstructsWithoutEndpoint() {
        assertThat(new OtelTracingMiddleware()).isNotNull();
    }
}
