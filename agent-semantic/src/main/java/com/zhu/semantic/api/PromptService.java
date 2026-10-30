package com.zhu.semantic.api;

import java.util.Map;

/**
 * 提示词读取。接入层通过它拿文案，拿不到时必须退回调用方给的 fallback。
 */
public interface PromptService {

    /**
     * @param key       提示词键
     * @param variables 模板变量，可以为空
     * @param fallback  外部配置不可用时的兜底，不能是空串
     */
    String get(String key, Map<String, String> variables, String fallback);
}
