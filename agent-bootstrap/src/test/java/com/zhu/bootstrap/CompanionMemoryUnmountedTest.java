package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory 迁入 ③：对话工厂不再依赖 / 挂载 CompanionMemoryMiddleware。
 */
class CompanionMemoryUnmountedTest {

    /** 构造参数与字段都不应再出现 Companion 记忆中间件。 */
    @Test
    void factory_noLongerDependsOnCompanionMemoryMiddleware() {
        assertThat(Arrays.stream(HarnessAgentFactory.class.getConstructors())
                .flatMap(ctor -> Arrays.stream(ctor.getParameterTypes())))
                .as("工厂构造不再注入 CompanionMemoryMiddleware")
                .doesNotContain(CompanionMemoryMiddleware.class);

        assertThat(Arrays.stream(HarnessAgentFactory.class.getDeclaredFields())
                .map(Field::getType))
                .as("工厂字段不再持有 CompanionMemoryMiddleware")
                .doesNotContain(CompanionMemoryMiddleware.class);
    }

    /** customizeCompanion 字节码侧无法稳定扫；至少保证工厂类型上无 companionMemory 成员名。 */
    @Test
    void factory_hasNoCompanionMemoryFieldName() {
        assertThat(Arrays.stream(HarnessAgentFactory.class.getDeclaredFields())
                .map(Field::getName))
                .doesNotContain("companionMemory");
        assertThat(Arrays.stream(HarnessAgentFactory.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain("setCompanionMemory");
    }
}
