package com.zhu.bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web 工具装配约定：公共项不关 web；echo 强制关；companion 仅在开关关闭时关。
 */
class WebToolsWiringTest {

    /**
     * 源码约定：configureCommon 不调用 disableWebTools；customizeEcho 必须调用。
     */
    @Test
    void factorySource_echoDisablesWeb_commonDoesNot() throws Exception {
        String source = readFactory();
        int commonStart = source.indexOf("public void configureCommon");
        assertThat(commonStart).isGreaterThanOrEqualTo(0);
        int echoStart = source.indexOf("public void customizeEcho", commonStart);
        assertThat(echoStart).isGreaterThan(commonStart);
        String common = source.substring(commonStart, echoStart);
        String echo = source.substring(echoStart);
        assertThat(common)
                .as("configureCommon 不再统一 disableWebTools")
                .doesNotContain("disableWebTools()");
        assertThat(common)
                .as("Shell 仍在公共项关闭")
                .contains("disableShellTool()");
        assertThat(echo)
                .as("echo 必须 disableWebTools")
                .contains("disableWebTools()");
    }

    /**
     * companion Binding：按 web-tools.enabled 条件关闭。
     */
    @Test
    void companionSource_gatesDisableWebToolsOnFlag() throws Exception {
        String source = readCompanion();
        assertThat(source)
                .as("companion 须读取 webTools 开关")
                .contains("getWebTools()")
                .contains("isEnabled()")
                .contains("disableWebTools()");
    }

    private static String readFactory() throws Exception {
        Path factory = Path.of("..", "agent-runtime", "src", "main", "java",
                        "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(factory)) {
            factory = Path.of("agent-runtime", "src", "main", "java",
                            "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java")
                    .toAbsolutePath()
                    .normalize();
        }
        return Files.readString(factory, StandardCharsets.UTF_8);
    }

    private static String readCompanion() throws Exception {
        Path path = Path.of("..", "agent-products", "agent-product-companion", "src", "main", "java",
                        "com", "zhu", "product", "companion", "binding", "CompanionResidentAgentBinding.java")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(path)) {
            path = Path.of("agent-products", "agent-product-companion", "src", "main", "java",
                            "com", "zhu", "product", "companion", "binding", "CompanionResidentAgentBinding.java")
                    .toAbsolutePath()
                    .normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
