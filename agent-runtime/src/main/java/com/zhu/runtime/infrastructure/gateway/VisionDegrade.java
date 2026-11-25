package com.zhu.runtime.infrastructure.gateway;

import com.zhu.runtime.api.agent.AgentRequest;
import io.agentscope.core.message.Msg;

/**
 * 看图失败后的纯文本重试。同步发送和流式重试共用，没图时不吞掉原错误。
 */
final class VisionDegrade {

    /** 流式降级时先推给调用方的状态句。 */
    static final String CLIENT_TIP = "未能看图，已按纯文本继续";

    /** 只提供静态方法，不让同步和流式各写一份是否重试的判断。 */
    private VisionDegrade() {
    }

    /**
     * 没有图片就原样抛出。有图片时返回纯文本重试消息。
     * 重试消息不走字符串发送重载，避免框架给降级轮补上首发没有的 name。
     */
    static Msg plainOrThrow(AgentRequest request, Throwable error) {
        if (request == null || !request.hasImages()) {
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException(error);
        }
        return AgentUserMessages.textOnly(AgentUserMessages.degradedText(request));
    }
}
