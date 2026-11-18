package com.zhu.runtime.api.agent;

/**
 * 运行层对外端口：业务配置热更后重建长驻 Agent。
 * 正式实现是 ChannelGateway；Nacos 监听只依赖本接口，不碰 Gateway 细节。
 */
public interface ResidentAgentReloader {

    /**
     * 排空在途对话后按当前配置重建 companion + echo。
     * 尚未懒启动过则 {@link RebuildResult.Status#SKIPPED}。
     * 排水超时仍会重建；成功时返回 {@link RebuildResult.Status#TIMEOUT}。
     * 失败返回 {@link RebuildResult.Status#FAILED}，尽量不向外抛业务异常。
     */
    RebuildResult rebuildAll();
}
