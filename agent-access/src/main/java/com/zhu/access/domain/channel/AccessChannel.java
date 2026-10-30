package com.zhu.access.domain.channel;

/**
 * 接入通道。只描述请求从哪进来，不传给运行层，运行层继续只认 AgentRequest。
 */
public enum AccessChannel {
    /** 页面或 HTTP 对话。缺省通道。 */
    WEB,
    /** 即时通讯。适配以后再接。 */
    IM,
    /** 程序调用。 */
    API
}
