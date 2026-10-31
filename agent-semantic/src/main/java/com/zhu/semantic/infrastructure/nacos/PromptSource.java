package com.zhu.semantic.infrastructure.nacos;

/**
 * 外部提示词原文。地址为空用缺省实现，配了地址用 Nacos 监听器。
 * 没配到返回 null，不要返回空串。抛错由 PromptService 接住并退回调用方的 fallback。
 */
public interface PromptSource {

    /**
     * @param key 提示词键，例如 prompt.platform.system
     * @return 未渲染的模板；没有这条键时返回 null
     */
    String load(String key);
}
