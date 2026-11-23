package com.zhu.runtime.infrastructure.agent;

import com.zhu.product.api.ProductModule;
import com.zhu.testsupport.StubProductModule;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 产品模块注册表：恰好一个 main、agentId 唯一、不占用 echo 标识。
 */
class ProductModuleRegistryTest {

    /** 单个 main 被收下，Find 能按标识回查。 */
    @Test
    void acceptsSingleMain() {
        ProductModule main = StubProductModule.main("companion-v1");
        ProductModuleRegistry registry = new ProductModuleRegistry(List.of(main));

        assertThat(registry.main()).isSameAs(main);
        assertThat(registry.all()).containsExactly(main);
        assertThat(registry.find("companion-v1")).isSameAs(main);
        assertThat(registry.find("nope")).isNull();
        assertThat(registry.find(" ")).isNull();
    }

    /** 没有 main 直接启动失败，避免 Gateway 没有主 Agent。 */
    @Test
    void rejectsMissingMain() {
        assertThatThrownBy(() -> new ProductModuleRegistry(List.of(StubProductModule.secondary("a"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected exactly one main ProductModule");
    }

    /** 两个 main 同样失败。 */
    @Test
    void rejectsMultipleMains() {
        assertThatThrownBy(() -> new ProductModuleRegistry(
                        List.of(StubProductModule.main("a"), StubProductModule.main("b"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected exactly one main ProductModule");
    }

    /** 标识重复会让状态写到同一个命名空间。 */
    @Test
    void rejectsDuplicateAgentId() {
        assertThatThrownBy(() -> new ProductModuleRegistry(
                        List.of(StubProductModule.main("a"), StubProductModule.secondary("a"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate ProductModule agentId=a");
    }

    /** echo 是平台探针，产品不得占用。 */
    @Test
    void rejectsReservedEchoId() {
        assertThatThrownBy(() -> new ProductModuleRegistry(
                        List.of(StubProductModule.main(HarnessAgentFactory.ECHO_AGENT_ID))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved echo id");
    }

    /** 空集合没有 main，也在启动期报错。 */
    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> new ProductModuleRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no ProductModule registered");
    }
}
