package com.zhu.semantic.infrastructure.prompt;

/**
 * 接收一条已经校验过的提示词。实现可以是测试替身，也可以是 Nacos。
 * 进程启动时不要调用。
 */
public interface PromptSink {

    /**
     * 写入一条模板原文。调用方已经校验过占位符和 JSON。
     */
    void publish(String key, String body);
}
