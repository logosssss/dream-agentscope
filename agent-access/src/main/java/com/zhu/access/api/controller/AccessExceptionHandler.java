package com.zhu.access.api.controller;

import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 接入层把可预期失败收成统一响应。业务失败 HTTP 仍是 200，调用方看 code。
 * 依赖不可用时 HTTP 503，调用方停止重试到这条链路。
 */
@RestControllerAdvice
public class AccessExceptionHandler {

    /** 业务失败不带数据。未分类的异常仍走容器默认处理。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> business(BusinessException error) {
        ApiResult<Void> body = ApiResult.fail(error.errorCode(), error.getMessage());
        if (error.errorCode() == ErrorCode.UNAVAILABLE) {
            return ResponseEntity.status(503).body(body);
        }
        return ResponseEntity.ok(body);
    }
}
