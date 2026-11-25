package com.zhu.runtime.infrastructure.gateway;

import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.channel.Channel;

/**
 * 从已启动的 ChannelManager 取出一条通道。运行层网关内部。
 * 白名单仍由调用方判断；这里只处理管理器为空、通道缺失和类型不对。
 */
final class BoundChannel {

    /** 只提供静态方法，避免 ChatUi 与 API 各写一遍类型校验。 */
    private BoundChannel() {
    }

    /**
     * 管理器为空就不能读名单。文案固定，供两条入口共用。
     */
    static ChannelManager present(ChannelManager manager) {
        if (manager == null) {
            throw new IllegalStateException("channel manager is missing");
        }
        return manager;
    }

    /**
     * 按期望类型取出通道。{@code bound} 为空表示这个 id 没有注册。
     * 类型不对时带上实际类名，避免静默换成另一种 Channel。
     */
    static <T extends Channel> T read(Channel bound, Class<T> type, String label) {
        if (bound == null) {
            throw new IllegalStateException(label + " channel missing");
        }
        if (!type.isInstance(bound)) {
            throw new IllegalStateException(label + " channel type mismatch: " + bound.getClass().getName());
        }
        return type.cast(bound);
    }
}
