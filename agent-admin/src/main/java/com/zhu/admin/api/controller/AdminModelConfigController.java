package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.ModelConfigRequest;
import com.zhu.audit.api.ModelConfigCatalog;
import com.zhu.audit.api.ModelConfigSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记模型配置。不接到对话，也不改正在用的模型名。
 */
@RestController
public class AdminModelConfigController {

    private final ObjectProvider<ModelConfigCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminModelConfigController(ObjectProvider<ModelConfigCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 新增 1.0.0。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/model-configs")
    public ApiResult<ModelConfigSummary> register(@RequestBody ModelConfigRequest request) {
        ModelConfigCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "model config is unavailable");
        }
        ModelConfigRequest body = request == null
                ? new ModelConfigRequest(null, null, null, null)
                : request;
        return ApiResult.ok(catalog.register(body.configId(), body.model(), body.provider(), body.tenantScope()));
    }
}
