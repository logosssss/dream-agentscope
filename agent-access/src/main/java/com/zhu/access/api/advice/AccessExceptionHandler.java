package com.zhu.access.api.advice;

import com.zhu.access.application.metrics.RequestMetrics;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 接入层把可预期失败收成统一响应。业务失败 HTTP 仍是 200，调用方看 code。
 * 未登录是 HTTP 401。依赖不可用时 HTTP 503。
 */
@RestControllerAdvice
public class AccessExceptionHandler {

    private final RequestMetrics metrics;

    /** 指标口可缺席（只跑单测时），但容器里一定有。 */
    @Autowired
    public AccessExceptionHandler(ObjectProvider<RequestMetrics> metrics) {
        this.metrics = metrics == null ? null : metrics.getIfAvailable();
    }

    /** 不带指标的构造：单测里只验状态码与响应体。 */
    public AccessExceptionHandler() {
        this.metrics = null;
    }

    /** 业务失败不带数据。未分类的异常仍走容器默认处理。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> business(BusinessException error) {
        record(error);
        ApiResult<Void> body = ApiResult.fail(error.errorCode(), error.getMessage());
        if (error.errorCode() == ErrorCode.UNAVAILABLE) {
            return ResponseEntity.status(503).body(body);
        }
        if (error.errorCode() == ErrorCode.UNAUTHORIZED) {
            return ResponseEntity.status(401).body(body);
        }
        return ResponseEntity.ok(body);
    }

    /** 拒绝指标按固定文案归类；归属失败不影响响应。 */
    private void record(BusinessException error) {
        if (metrics == null) {
            return;
        }
        try {
            metrics.rejected(RequestMetrics.reason(error.getMessage()), error.errorCode().name());
        } catch (RuntimeException ignored) {
            // 指标失败不能把 200/401/503 变成 500。
        }
    }
}
