package com.zhu.runtime.domain;

import com.zhu.semantic.contract.safety.RiskLevel;

/**
 * 一轮对话里中间件写给收口看的信号。
 *
 * <p>为什么需要它：中间件拿到的是 Gateway 合成的 {@code RuntimeContext}，
 * 而收口（{@code AgentTurnSupport.finish}）拿的是调用线程上那份本地上下文——两个不同的对象。
 * 早先把档位直接 {@code put} 进框架上下文，收口读自己那份永远是空的，
 * 于是危机输出红线与人设检查在生产恒不触发。这里改成一个共享的小对象：中间件写、收口读。
 *
 * <p>只活一轮：入口建一个，随 caller 暂存带进网关，结束后丢掉，不做跨轮缓存。
 * 字段用 volatile：网关的中间件可能跑在别的线程上。
 */
public final class TurnSignals {

    private volatile RiskLevel risk = RiskLevel.NORMAL;

    private volatile boolean personaCheck;

    /** 这一轮的档位。没写过按普通陪伴，不误换话术。 */
    public RiskLevel risk() {
        return risk;
    }

    /** 安全门写入判定结果。null 视为普通陪伴。 */
    public void risk(RiskLevel level) {
        this.risk = level == null ? RiskLevel.NORMAL : level;
    }

    /** 收口要不要查人设。 */
    public boolean personaCheck() {
        return personaCheck;
    }

    /** 人设中间件在触发条件成立时打开。 */
    public void personaCheck(boolean check) {
        this.personaCheck = check;
    }
}
