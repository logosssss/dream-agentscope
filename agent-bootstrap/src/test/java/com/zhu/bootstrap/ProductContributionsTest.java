package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.product.api.ProductModule;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/**
 * 产品入口声明的失败路径。不启动 Spring，避免为了证明失败去改生产模块。
 */
class ProductContributionsTest {

    /** 声明了类但容器里没有 Bean 时，异常文案带产品 id 和类名。 */
    @Test
    void missingBeanFailsWithProductIdAndClassName() {
        ProductModule module = new GhostModule();
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansOfType(MissingController.class)).thenReturn(Map.of());

        assertThatThrownBy(() -> ProductContributions.validate(List.of(module), context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost-v1")
                .hasMessageContaining(MissingController.class.getName());
    }

    /** 只为校验准备的产品。不进组件扫描。 */
    private static final class GhostModule implements ProductModule {

        @Override
        public String agentId() {
            return "ghost-v1";
        }

        @Override
        public boolean mainAgent() {
            return false;
        }

        @Override
        public List<ToolDescriptor> tools() {
            return List.of();
        }

        @Override
        public CompletionCriteria completion() {
            return new CompletionCriteria(1);
        }

        @Override
        public String promptFallback(String key) {
            return "ghost";
        }

        @Override
        public void customize(HarnessAgent.Builder builder) {
            // 测试不装配 Agent。
        }

        @Override
        public List<Class<?>> controllers() {
            return List.of(MissingController.class);
        }
    }

    /** 故意不是 Spring Bean。 */
    private static final class MissingController {
    }
}
