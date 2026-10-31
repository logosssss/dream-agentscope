package com.zhu.semantic.contract;

/**
 * 安全门的风险档。运行层按档换话术，不把判定细节交给模型。
 */
public enum RiskLevel {

    /** 普通陪伴。继续用搭子人设。 */
    NORMAL,

    /** 升高留意。仍用搭子话术，只加克制标记。语义判定或判定失败会到这一档。 */
    ELEVATED,

    /** 危机。换成 classpath 危机话术，不再用搭子人设。 */
    CRITICAL
}
