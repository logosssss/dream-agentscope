package com.zhu.bootstrap;

import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.infrastructure.agent.CatalogToolRegistrar;
import com.zhu.runtime.infrastructure.tool.KnowledgeSearchTool;
import com.zhu.semantic.contract.ProductTool;
import com.zhu.semantic.contract.ToolDescriptor;
import com.zhu.semantic.infrastructure.tool.EchoTextTool;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorStore;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 只有目录里点名的业务工具会进 Toolkit。不连接模型。
 */
class ToolRegistrationTest {

    /** 生产回声工具的标识和注解名必须一致。 */
    @Test
    void echoTextToolIdMatchesAnnotation() {
        EchoTextTool echo = new EchoTextTool();
        assertThat(echo.toolId()).isEqualTo(EchoTextTool.TOOL_ID);
        assertThat(echo.toolId()).isEqualTo("echo_text");
        assertThat(echo.target()).isSameAs(echo);
        assertThat(echo.echo("ping")).isEqualTo("ping");
        assertThat(echo.echo(null)).isEmpty();
    }

    /** 知识检索工具标识与注解名一致，可按目录注册。 */
    @Test
    void knowledgeSearchRegistersFromCatalog() {
        KnowledgeSearchTool search = new KnowledgeSearchTool(
                mock(VectorStore.class), mock(KnowledgeRepository.class), mock(TextEmbedder.class));
        assertThat(search.toolId()).isEqualTo(KnowledgeSearchTool.TOOL_ID);
        Toolkit toolkit = new Toolkit();
        new CatalogToolRegistrar().register(
                toolkit,
                List.of(new ToolDescriptor(KnowledgeSearchTool.TOOL_ID, "知识检索", "检索")),
                List.of(search));
        assertThat(toolkit.getTool(KnowledgeSearchTool.TOOL_ID)).isNotNull();
    }

    /** 目录点名的工具能查到。同一次注册里标识重复时留下第一个。 */
    @Test
    void registersOnlyCatalogTools() {
        Toolkit toolkit = new Toolkit();
        EchoTextTool echo = new EchoTextTool();
        new CatalogToolRegistrar().register(
                toolkit,
                List.of(new ToolDescriptor(EchoTextTool.TOOL_ID, "回声", "原样返回")),
                List.of(echo, new HiddenTool(), echo));

        assertThat(toolkit.getTool(EchoTextTool.TOOL_ID)).isNotNull();
        assertThat(toolkit.getTool("hidden_text")).isNull();
    }

    /** 目录为空时，即使有实现也不注册。另一个 Toolkit 不受影响。 */
    @Test
    void emptyCatalogRegistersNothing() {
        Toolkit untouched = new Toolkit();
        Toolkit target = new Toolkit();
        new CatalogToolRegistrar().register(target, List.of(), List.of(new EchoTextTool()));

        assertThat(target.getTool(EchoTextTool.TOOL_ID)).isNull();
        assertThat(untouched.getTool(EchoTextTool.TOOL_ID)).isNull();
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
