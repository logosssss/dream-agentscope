package com.zhu.runtime.domain;

import com.zhu.semantic.contract.safety.CrisisPrompt;
import java.util.List;
import java.util.Locale;

/**
 * 危机档模型回复的输出红线。词表留在代码里，不进 Nacos，也不调模型。
 * 只检查候选回复。进门危机词仍由 KeywordRiskClassifier 处理。
 * 盖掉之后不再回头检查预置稿，避免话术里的「不要开玩笑」把自己误伤。
 */
public final class CrisisReplyGuard {

    /** 玩笑口吻。危机里不能把难受当段子。 */
    private static final List<String> JOKES = List.of(
            "开玩笑",
            "开个玩笑",
            "逗你玩",
            "笑一个",
            "哈哈这");

    /** 暧昧口吻。危机里不能往亲密关系靠。 */
    private static final List<String> INTIMACY = List.of(
            "亲爱的",
            "宝贝",
            "想你了",
            "抱一下",
            "亲亲",
            "么么");

    /** 角色扮演。危机里不能把对方推进剧情。 */
    private static final List<String> ROLEPLAY = List.of(
            "角色扮演",
            "我们来演",
            "假如你是",
            "你来扮演",
            "进入角色",
            "假装你是");

    /** 危险方法。短词单独成条，不用「死」「方法」这种过宽的字。 */
    private static final List<String> METHODS = List.of(
            "怎么死",
            "怎么做",
            "具体方法",
            "操作步骤",
            "配比",
            "割腕",
            "跳楼",
            "上吊",
            "烧炭",
            "服毒");

    private CrisisReplyGuard() {
    }

    /**
     * 非危机原样过占位符检查。危机命中红线则换成预置稿。
     * 预置稿空白时用代码兜底，不能把空串交给用户。
     */
    public static String finalize(String reply, boolean critical, String crisisText) {
        String cleaned = ModelTextGuard.outbound(reply);
        if (!critical || !blocked(cleaned)) {
            return cleaned;
        }
        if (crisisText == null || crisisText.isBlank()) {
            return CrisisPrompt.FALLBACK;
        }
        return crisisText;
    }

    /**
     * 命中任一类就是红线。空回复不拦，避免空白也换成稿。
     * 不把回复正文写进日志。
     */
    static boolean blocked(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String normalized = reply.toLowerCase(Locale.ROOT);
        return containsAny(normalized, JOKES)
                || containsAny(normalized, INTIMACY)
                || containsAny(normalized, ROLEPLAY)
                || containsAny(normalized, METHODS);
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
