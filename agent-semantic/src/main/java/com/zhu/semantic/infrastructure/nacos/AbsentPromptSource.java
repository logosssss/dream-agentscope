package com.zhu.semantic.infrastructure.nacos;

import org.springframework.stereotype.Component;

/**
 * 还没有 Nacos 地址时的提示词来源。每次都表示未配置，让 PromptService 走 fallback。
 * 容器里只能有一个 PromptSource。配上服务端地址后再换成监听器，不要两个一起注册。
 */
@Component
public class AbsentPromptSource implements PromptSource {

    /**
     * 不访问网络。返回 null 表示这条键不存在，调用方据此使用自己的兜底文案。
     */
    @Override
    public String load(String key) {
        return null;
    }
}
