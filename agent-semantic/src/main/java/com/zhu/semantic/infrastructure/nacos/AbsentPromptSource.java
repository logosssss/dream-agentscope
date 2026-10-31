package com.zhu.semantic.infrastructure.nacos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 没有 Nacos 地址时的提示词来源。每次都表示未配置，让 PromptService 走 fallback。
 * 和监听器互斥。地址一旦配上，就只注册监听器。
 */
@Component
@ConditionalOnExpression("!T(org.springframework.util.StringUtils).hasText('${agentscope.nacos.server-addr:}')")
public class AbsentPromptSource implements PromptSource {

    /**
     * 不访问网络。返回 null 表示这条键不存在，调用方据此使用自己的兜底文案。
     */
    @Override
    public String load(String key) {
        return null;
    }
}
