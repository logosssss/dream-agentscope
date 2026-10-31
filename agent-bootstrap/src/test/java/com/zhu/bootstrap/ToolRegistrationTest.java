package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.agent.CatalogToolRegistrar;
import com.zhu.semantic.contract.ProductTool;
import com.zhu.semantic.contract.ToolDescriptor;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只有目录里点名的业务工具会进 Toolkit。不连接模型。
 */
class ToolRegistrationTest {

    /** 目录点名的工具能查到。同一次注册里标识重复时留下第一个。 */
    @Test
    void registersOnlyCatalogTools() {
        Toolkit toolkit = new Toolkit();
        EchoTool echo = new EchoTool();
        new CatalogToolRegistrar().register(
                toolkit,
                List.of(new ToolDescriptor("echo_text", "回声", "原样返回")),
                List.of(echo, new HiddenTool(), echo));

        assertThat(toolkit.getTool("echo_text")).isNotNull();
        assertThat(toolkit.getTool("hidden_text")).isNull();
    }

    /** 目录为空时，即使有实现也不注册。另一个 Toolkit 不受影响。 */
    @Test
    void emptyCatalogRegistersNothing() {
        Toolkit untouched = new Toolkit();
        Toolkit target = new Toolkit();
        new CatalogToolRegistrar().register(target, List.of(), List.of(new EchoTool()));

        assertThat(target.getTool("echo_text")).isNull();
        assertThat(untouched.getTool("echo_text")).isNull();
    }

    /** 测试用的回声工具。toolId 和注解名相同。 */
    public static final class EchoTool implements ProductTool {

        /** 目录里的标识。 */
        @Override
        public String toolId() {
            return "echo_text";
        }

        /** 工具方法就在这个对象上。 */
        @Override
        public Object target() {
            return this;
        }

        /** 原样返回。测试只验证注册，不调用模型。 */
        @Tool(name = "echo_text", description = "原样返回")
        public String echo(@ToolParam(name = "text", description = "原文") String text) {
            return text;
        }
    }

    /** 不在目录里，不应该出现在 Toolkit。 */
    public static final class HiddenTool implements ProductTool {

        /** 和目录无关的标识。 */
        @Override
        public String toolId() {
            return "hidden_text";
        }

        /** 工具方法就在这个对象上。 */
        @Override
        public Object target() {
            return this;
        }

        /** 不会被注册。 */
        @Tool(name = "hidden_text", description = "隐藏")
        public String hidden(@ToolParam(name = "text", description = "原文") String text) {
            return text;
        }
    }
}
