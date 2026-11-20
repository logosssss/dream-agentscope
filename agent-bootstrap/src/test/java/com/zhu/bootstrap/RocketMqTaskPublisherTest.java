package com.zhu.bootstrap;

import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.publish.RocketMqTaskPublisher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * mq 占位不发送消息，也不把 RocketMQ 客户端带进 classpath。
 */
class RocketMqTaskPublisherTest {

    /** 发布直接失败，说明还要补依赖、地址和消费者。 */
    @Test
    void publishRefusesWithoutClient() {
        RocketMqTaskPublisher publisher = new RocketMqTaskPublisher();

        assertThatThrownBy(() -> publisher.publish(new AgentTask(
                        "task-1",
                        "doc-ingest",
                        "tenant-a",
                        "user-a",
                        null,
                        null,
                        null,
                        Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rocketmq-spring-boot-starter")
                .hasMessageContaining(RocketMqTaskPublisher.TOPIC);
    }

    /** 启动阶段同样失败，避免进程在 mode=mq 下空转。 */
    @Test
    void startupRefusesMqMode() {
        assertThatThrownBy(() -> new RocketMqTaskPublisher().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rocketmq.name-server");
    }

    /** 客户端类不在当前 classpath。 */
    @Test
    void rocketMqClientIsAbsent() {
        assertThatThrownBy(() -> Class.forName("org.apache.rocketmq.spring.core.RocketMQTemplate"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(RocketMqTaskPublisher.TOPIC).isEqualTo("agentscope-tasks");
    }
}
