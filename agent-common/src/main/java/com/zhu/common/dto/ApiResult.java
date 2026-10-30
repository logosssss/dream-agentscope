package com.zhu.common.dto;

import com.zhu.common.exception.ErrorCode;

/**
 * 接入层统一响应。成功和失败都走同一结构，前端只看 {@code ok} 和 {@code code}。
 *
 * @param ok      是否成功
 * @param code    成功为 OK，失败为 {@link ErrorCode} 名称
 * @param message 失败说明，成功时为空
 * @param data    成功时的载荷
 */
public record ApiResult<T>(boolean ok, String code, String message, T data) {

    /** 成功响应。 */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "OK", null, data);
    }

    /** 失败响应，不携带业务数据。 */
    public static <T> ApiResult<T> fail(ErrorCode code, String message) {
        return new ApiResult<>(false, code.name(), message, null);
    }
}
