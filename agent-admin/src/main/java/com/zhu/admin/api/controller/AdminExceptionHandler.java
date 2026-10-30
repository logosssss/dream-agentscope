package com.zhu.admin.api.controller;

import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理进程把可预期失败收成统一响应。HTTP 仍是 200，调用方看 code。
 */
@RestControllerAdvice
public class AdminExceptionHandler {

    /** 业务失败不带数据。未分类的异常仍走容器默认处理。 */
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> business(BusinessException error) {
        return ApiResult.fail(error.errorCode(), error.getMessage());
    }
}
