package com.zhu.access.application;

/**
 * 微信换票。测试用假实现，正式进程才访问微信接口。
 */
public interface WxRemote {

    /**
     * 用小程序 code 换 openid。失败时抛业务异常，不返回空串。
     */
    String openId(String jsCode);

    /**
     * 生成确认页的小程序码。scene 是票号。失败时抛业务异常。
     */
    byte[] qrCode(String scene);
}
