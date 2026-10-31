package com.zhu.bootstrap;

import com.zhu.runtime.domain.KeywordRiskClassifier;
import com.zhu.semantic.contract.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 关键词只识别危机。普通闲聊不能抬档。不连模型。
 */
class KeywordRiskClassifierTest {

    /** 自伤、自杀词走危机。空输入仍是普通陪伴。 */
    @Test
    void criticalWordsSwitchAndBlankStaysNormal() {
        assertThat(KeywordRiskClassifier.classify("我想自杀")).isEqualTo(RiskLevel.CRITICAL);
        assertThat(KeywordRiskClassifier.classify("活不下去了")).isEqualTo(RiskLevel.CRITICAL);
        assertThat(KeywordRiskClassifier.classify("今天天气不错")).isEqualTo(RiskLevel.NORMAL);
        assertThat(KeywordRiskClassifier.classify(" ")).isEqualTo(RiskLevel.NORMAL);
        assertThat(KeywordRiskClassifier.classify(null)).isEqualTo(RiskLevel.NORMAL);
    }
}
