package com.zhu.admin.api.controller.support;

import com.zhu.admin.application.AdminOperationAuditHook;
import com.zhu.common.dto.ApiResult;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 管理进程在成功响应写出前补记后台操作。失败响应和查询不走这里的目录写入。
 */
@ControllerAdvice
public class AdminOperationAuditAdvice implements ResponseBodyAdvice<Object> {

    /** 还没有用户登录，操作人只认这个头。 */
    public static final String OPERATOR_HEADER = "X-Operator-Id";

    private final AdminOperationAuditHook hook;

    /** 钩子负责认路径和吞掉写失败。 */
    public AdminOperationAuditAdvice(AdminOperationAuditHook hook) {
        this.hook = hook;
    }

    /**
     * 所有控制器响应都进来，是否记账看成功标志和路径。
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 只在 ok=true 时记账。请求头没有操作人时由钩子写成 admin。
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResult<?> result && result.ok()) {
            hook.afterSuccess(
                    request.getMethod().name(),
                    request.getURI().getPath(),
                    request.getHeaders().getFirst(OPERATOR_HEADER));
        }
        return body;
    }
}
