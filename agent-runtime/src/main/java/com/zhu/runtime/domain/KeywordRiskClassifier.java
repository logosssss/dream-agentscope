package com.zhu.runtime.domain;

import com.zhu.semantic.contract.safety.RiskLevel;
import java.util.List;
import java.util.Locale;

/**
 * 关键词风险判定。词表留在代码里，不靠模型，也不读配置。
 * 这一块只识别危机词。升高档留给以后的语义判定。
 */
public final class KeywordRiskClassifier {

    /**
     * 自伤、自杀和危险方法。短词单独成条，避免用「死」这种过宽的字。
     */
    private static final List<String> CRITICAL_WORDS = List.of(
            "自杀",
            "自尽",
            "轻生",
            "不想活",
            "活不下去",
            "结束自己",
            "自残",
            "自伤",
            "割腕",
            "跳楼",
            "上吊",
            "烧炭",
            "服毒",
            "寻死",
            "去死",
            "怎么死",
            "安乐死");

    private KeywordRiskClassifier() {
    }

    /**
     * 命中任一词就是危机。空输入按普通陪伴，避免空串误触发。
     */
    public static RiskLevel classify(String text) {
        if (text == null || text.isBlank()) {
            return RiskLevel.NORMAL;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String word : CRITICAL_WORDS) {
            if (normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return RiskLevel.CRITICAL;
            }
        }
        return RiskLevel.NORMAL;
    }
}
