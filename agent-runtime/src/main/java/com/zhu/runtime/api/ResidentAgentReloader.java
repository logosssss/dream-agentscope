package com.zhu.runtime.api;

/**
 * 运行层：业务配置热更后重建长驻 Agent。
 * 正式实现是 {@code ChannelGateway}；Nacos 监听只依赖本接口，不碰 Gateway 细节。
 */
public interface ResidentAgentReloader {

    /**
     * 排空在途对话后按当前配置重建 companion + echo。
     * 尚未懒启动过则空操作。
     */
    void rebuildAll();
}
