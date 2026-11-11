package com.zhu.access.application.chat;

/**
 * 接入层长驻 Agent 重建缝。业务配置热更后由 Watcher 调用；未启用 Channel 时无实现。
 */
public interface ResidentAgentReloader {

    /**
     * 排空进行中的对话后重建全部长驻 Agent（companion + echo）。
     * 从未启动过则空操作。排空超时仍替换，避免热更卡死。
     */
    void rebuildAll();
}
