package com.zhu.common.exception;

/**
 * 跨模块共用的错误码。业务失败用它，不把 HTTP 状态散落在各层。
 */
public enum ErrorCode {
    /** 入参缺失或格式不对。 */
    BAD_REQUEST,
    /** 未登录或身份无效。 */
    UNAUTHORIZED,
    /** 目标不存在。 */
    NOT_FOUND,
    /** 与当前状态冲突，例如重复提交。 */
    CONFLICT,
    /** 当日额度或限流窗口已满。 */
    QUOTA_EXCEEDED,
    /** 依赖暂时不可用。接入层用 HTTP 503 表示，不降级到别的存储。 */
    UNAVAILABLE,
    /** 未分类的内部失败。 */
    INTERNAL
}
