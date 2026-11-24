package com.zhu.semantic.api.prompt;

import com.zhu.semantic.contract.safety.CrisisPrompt;
import java.util.Set;

/**
 * 平台级提示词键。搭子主对话和危机话术的变量约定登记在这里。
 * 危机键只给校验用，不发布到 Nacos。
 */
public final class PlatformPrompt {

    /** 搭子主对话提示词。请求体不能改这条。 */
    public static final String SYSTEM_KEY = "prompt.companion.system";

    /**
     * 外部配置没有这条键时的兜底。和仓库里的模板一致，不能是空串。
     */
    public static final String SYSTEM_FALLBACK =
            "你是用户的智能搭子，不是冰冷的工具助手，也不是恋人。语气自然、克制、不卖惨。不替用户做决定。不讨论自伤或危险方法。关系从相识开始，不由你自己升级。\n"
                    + "\n"
                    + "凡涉及本平台设计文档、落地记录、架构说明，或用户问你「能不能/会不会/可以查文档」：必须先调用 docs_search（query 可用用户原话），再依据工具结果作答，并在答案中给出 `/docs/doc/{docId}` 链接。禁止只口头承诺可以查却不调用工具。无结果时如实说没找到，不要编造。";

    /** 危机话术键。和 {@link CrisisPrompt#KEY} 同一条，不走外部配置。 */
    public static final String CRISIS_KEY = CrisisPrompt.KEY;

    private PlatformPrompt() {
    }

    /**
     * 代码读取这条键时会传入的变量名。系统提示词当前不传变量，模板里不能留占位符。
     * 没有登记的键返回 null，仓库里的模板因此不能单独加变量。
     */
    public static Set<String> allowedVariables(String key) {
        if (SYSTEM_KEY.equals(key) || CRISIS_KEY.equals(key) || "prompt.platform.system".equals(key)) {
            return Set.of();
        }
        return null;
    }

    /**
     * 危机话术只留在仓库，发布端不能把它推到 Nacos。
     */
    public static boolean publishable(String key) {
        return !CRISIS_KEY.equals(key);
    }
}
