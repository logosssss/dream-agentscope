package com.zhu.semantic.contract.safety;

/**
 * 语义风险判定。运行层只依赖这个口，测试注入假实现，不连模型。
 * 关键词已经是危机时，调用方不会走到这里。
 */
public interface SemanticRiskJudge {

    /**
     * 只看用户原文。失败由调用方按升高处理，这里不要吞掉异常。
     * 返回 null 时调用方按普通陪伴。
     */
    RiskLevel judge(String text);
}
