package com.zhu.runtime.task;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 发布口的占位。mode=mq 时创建，但启动阶段就失败。
 * 不引入 rocketmq 客户端，避免进程在没配队列时把任务当成已经发出。
 */
@Component
@ConditionalOnProperty(name = "agentscope.task.mode", havingValue = "mq")
public class RocketMqTaskPublisher implements AgentTaskPublisher, ApplicationRunner {

    /** 以后发送和消费使用的 topic。现在没有客户端，不会真正建主题。 */
    public static final String TOPIC = "agentscope-tasks";

    /**
     * 启动时拒绝继续。mode=sync 不会创建这个 Bean。
     */
    @Override
    public void run(ApplicationArguments args) {
        throw notReady();
    }

    /**
     * 不发送消息。调用方应保持 mode=sync，直到客户端、地址和消费者都接上。
     */
    @Override
    public void publish(AgentTask task) {
        throw notReady();
    }

    /** 三步都没做完就不能切 mq。文案里不放真实地址。 */
    private static IllegalStateException notReady() {
        return new IllegalStateException(
                "agentscope.task.mode=mq is not available yet; add rocketmq-spring-boot-starter, "
                        + "set rocketmq.name-server from the environment, and consume topic "
                        + TOPIC
                        + " with the existing AgentTaskHandler");
    }
}
