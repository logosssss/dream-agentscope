package com.zhu.semantic.config.safety;

import com.zhu.semantic.contract.safety.RiskLevel;
import com.zhu.semantic.contract.safety.SemanticRiskJudge;
import org.springframework.stereotype.Component;

/**
 * 生产占位。不访问模型，也不把闲聊抬档。
 * 真正的语义判定以后再替换这个实现。
 */
@Component
public class AbsentSemanticRiskJudge implements SemanticRiskJudge {

    /** 没有判定器时不当成危机，避免误换话术。 */
    @Override
    public RiskLevel judge(String text) {
        return RiskLevel.NORMAL;
    }
}
