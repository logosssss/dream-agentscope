package com.zhu.docs.api;

import java.util.function.Supplier;

/**
 * 提问前的额度与并发。文档库不碰 Redis，接入层用对话同一套计数实现。
 */
public interface DocsAskGate {

    /**
     * 先过租户和额度，再执行补全。名额在动作结束时归还，失败也归还。
     * 动作名固定是 {@code docs-ask}：当日请求、token、费用与对话共用，每分钟的桶分开。
     *
     * @param tenantId 当前租户，匿名场景不会进到这里
     * @param userId   当前用户
     * @param action   真正调用模型的那段
     */
    <T> T enter(String tenantId, String userId, Supplier<T> action);
}
