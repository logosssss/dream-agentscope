package com.zhu.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 语义边界测试。Nacos 推送可能语法合法但语义非法，绑定后必须当场拒绝，否则热更会把
 * 运行层带进「跑不起来」的状态（例如 max-iters=0 让 Agent 一次都不迭代）。
 */
class AgentRuntimePropertiesTest {

    /** 默认值必须自洽：模板直接启动时不能因为校验把进程拦下。 */
    @Test
    void defaultsPassValidation() {
        assertThatCode(() -> new AgentRuntimeProperties().validate()).doesNotThrowAnyException();
    }

    /** 空模型无法装配 Agent，属于启动即错。 */
    @Test
    void blankModelIsRejected() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setModel("  ");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentscope.runtime.model");
    }

    /** 空 agentId 无法命名状态空间与会话路由。 */
    @Test
    void blankAgentIdIsRejected() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setAgentId("");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentscope.runtime.agent-id");
    }

    /** 0 次迭代等于不调用模型，静默返回空回复，必须当配置错误。 */
    @Test
    void zeroMaxIterationsIsRejected() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setMaxIters(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentscope.runtime.max-iters");
    }

    /** 0 秒超时会让每个请求立刻失败，同样当配置错误。 */
    @Test
    void zeroTimeoutIsRejected() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setTimeoutSeconds(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentscope.runtime.timeout-seconds");
    }

    /** 边界值 1 本身合法：只拒绝 < 1，不把最小可用配置当成非法。 */
    @Test
    void minimumPositiveValuesAreAccepted() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setMaxIters(1);
        properties.setTimeoutSeconds(1);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.getMaxIters()).isEqualTo(1);
    }
}
