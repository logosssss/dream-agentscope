package com.zhu.semantic.api;

import java.util.Set;

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

    /**
     * 代码读取这条键时会传入的变量名。系统提示词当前不传变量，模板里不能留占位符。
     * 没有登记的键返回 null，仓库里的模板因此不能单独加变量。
     */
    public static Set<String> allowedVariables(String key) {
        if (SYSTEM_KEY.equals(key)) {
            return Set.of();
        }
        return null;
    }
}
