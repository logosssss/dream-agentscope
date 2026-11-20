package com.zhu.runtime.infrastructure.agent;

import com.zhu.semantic.contract.tool.ProductTool;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 目录工具注册隔离测。单工具抛错不得挡住其余工具。
 */
class CatalogToolRegistrarTest {

    /**
     * 坏工具先失败后，好工具仍能挂上；报告同时列出 registered 与 failed。
     */
    @Test
    void register_isolatesSingleToolFailure() {
        Toolkit toolkit = new Toolkit();
        CatalogToolRegistrationReport report = new CatalogToolRegistrar().register(
                toolkit,
                List.of(
                        new ToolDescriptor("boom_tool", "坏", "抛错"),
                        new ToolDescriptor("ok_tool", "好", "正常")),
                List.of(new BoomTool(), new OkTool()));

        assertThat(report.failed()).containsExactly("boom_tool");
        assertThat(report.registered()).containsExactly("ok_tool");
        assertThat(toolkit.getTool("ok_tool")).isNotNull();
        assertThat(toolkit.getTool("boom_tool")).isNull();
    }

    /** target() 抛错的假工具。 */
    static final class BoomTool implements ProductTool {

        @Override
        public String toolId() {
            return "boom_tool";
        }

        @Override
        public Object target() {
            throw new IllegalStateException("boom");
        }
    }

    /** 可正常注册的假工具。 */
    static final class OkTool implements ProductTool {

        @Override
        public String toolId() {
            return "ok_tool";
        }

        @Override
        public Object target() {
            return this;
        }

        @Tool(name = "ok_tool", description = "好")
        public String ok(@ToolParam(name = "text", description = "原文") String text) {
            return text == null ? "" : text;
        }
    }
}
