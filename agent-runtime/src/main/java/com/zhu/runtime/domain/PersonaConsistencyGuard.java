package com.zhu.runtime.domain;

import com.zhu.semantic.contract.product.PersonaContract;
import java.util.List;
import java.util.Locale;

/**
 * 人设漂移的规则检查。词表留在代码里，不进 Nacos，也不调模型。
 * 只检查候选回复。危机档不要走这里。
 * 盖掉之后不再回头检查基线，避免「不是恋人」把自己误伤。
 */
public final class PersonaConsistencyGuard {

    /** 契约读不到时的短基线。和锁定人格同一句话，不另写人设。 */
    static final String DEFAULT_BASELINE = "你是用户的智能搭子，不是工具助手，也不是恋人。自然、克制、不卖惨。";

    /** 自称恋人，或把关系往上升。 */
    private static final List<String> LOVER = List.of(
            "我是你的恋人",
            "我是你女朋友",
            "我是你男朋友",
            "我们是恋人",
            "升级成恋人",
            "我们在一起吧");

    /** 自称工具助手，丢掉搭子身份。 */
    private static final List<String> ASSISTANT = List.of(
            "我是工具助手",
            "我是你的助手",
            "作为助手我");

    /** 替用户做决定。 */
    private static final List<String> DECIDE = List.of(
            "我替你决定",
            "我来替你做主",
            "听我的就好");

    private PersonaConsistencyGuard() {
    }

    /**
     * 未启用时只过占位符。启用且命中漂移则换成短基线。
     * 基线空白时用代码兜底，不能把空串交给用户。
     */
    public static String finalize(String reply, boolean enabled, String baseline) {
        String cleaned = ModelTextGuard.outbound(reply);
        if (!enabled || !drifted(cleaned)) {
            return cleaned;
        }
        if (baseline == null || baseline.isBlank()) {
            return DEFAULT_BASELINE;
        }
        return baseline;
    }

    /**
     * 身份加语气拼成短基线。缺身份时用代码兜底。
     * 不把禁区拼进去，避免检查词和基线缠在一起。
     */
    public static String baseline(PersonaContract persona) {
        if (persona == null) {
            return DEFAULT_BASELINE;
        }
        String identity = persona.coreIdentity() == null ? "" : persona.coreIdentity().strip();
        String style = persona.speechStyle() == null ? "" : persona.speechStyle().strip();
        if (identity.isBlank()) {
            return DEFAULT_BASELINE;
        }
        if (style.isBlank()) {
            return identity;
        }
        return identity + style;
    }

    /**
     * 命中任一类就是漂移。空回复不拦。
     * 不把回复正文写进日志。
     */
    static boolean drifted(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String normalized = reply.toLowerCase(Locale.ROOT);
        return containsAny(normalized, LOVER)
                || containsAny(normalized, ASSISTANT)
                || containsAny(normalized, DECIDE);
    }

    /** 大小写已经折过。按包含匹配，不做分词。 */
    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
