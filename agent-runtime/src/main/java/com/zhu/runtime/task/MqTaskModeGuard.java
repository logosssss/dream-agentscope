package com.zhu.runtime.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mode=mq 还不能启动。队列实现和 rocketmq 依赖要等真正切换时再加，避免进程以为任务已经发出。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.task.mode", havingValue = "mq")
public class MqTaskModeGuard {

    /**
     * 创建发布口时直接失败。调用方不会拿到一个什么都不做的实现。
     */
    @Bean
    public AgentTaskPublisher mqPublisherNotReady() {
        throw new IllegalStateException(
                "agentscope.task.mode=mq is not available yet; keep mode=sync until the RocketMQ starter is added");
    }
}
