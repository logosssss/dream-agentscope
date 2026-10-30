package com.zhu.semantic.api;

/**
 * 平台级提示词键。产品自己的话术不放这里，由调用方传入 key 和 fallback。
 */
public final class PlatformPrompt {

    /** 请求没带系统提示词时使用的键，命名遵循 prompt.{agent}.{purpose}。 */
    public static final String SYSTEM_KEY = "prompt.platform.system";

    /**
     * 外部配置没有这条键时的兜底。不能是空串，空提示词比占位符更危险。
     */
    public static final String SYSTEM_FALLBACK = ".";

    private PlatformPrompt() {
    }
}
