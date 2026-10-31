package com.zhu.semantic.api;

import java.util.Set;

/**
 * 平台级提示词键。产品自己的话术不放这里，由调用方传入 key 和 fallback。
 */
public final class PlatformPrompt {

    /** 搭子主对话提示词。请求体不能改这条。 */
    public static final String SYSTEM_KEY = "prompt.companion.system";

    /**
     * 外部配置没有这条键时的兜底。和仓库里的模板一致，不能是空串。
     */
    public static final String SYSTEM_FALLBACK =
            "你是用户的智能搭子，不是工具助手，也不是恋人。语气自然、克制、不卖惨。不替用户做决定。不讨论自伤或危险方法。关系从相识开始，不由你自己升级。";

    private PlatformPrompt() {
    }

    /**
     * 代码读取这条键时会传入的变量名。系统提示词当前不传变量，模板里不能留占位符。
     * 没有登记的键返回 null，仓库里的模板因此不能单独加变量。
     */
    public static Set<String> allowedVariables(String key) {
        if (SYSTEM_KEY.equals(key) || "prompt.platform.system".equals(key)) {
            return Set.of();
        }
        return null;
    }
}
