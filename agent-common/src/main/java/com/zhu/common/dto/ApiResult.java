package com.zhu.common.dto;

import com.zhu.common.exception.ErrorCode;

public record ApiResult<T>(boolean ok, String code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "OK", null, data);
    }

    public static <T> ApiResult<T> fail(ErrorCode code, String message) {
        return new ApiResult<>(false, code.name(), message, null);
    }
}
