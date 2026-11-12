package com.zhu.admin.api.controller.model;

import com.zhu.admin.api.dto.model.ModelConfigRequest;
import com.zhu.audit.api.model.ModelConfigCatalog;
import com.zhu.audit.api.model.ModelConfigSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记、停用、恢复和查看模型配置。不解析参数。用户进程自己去选用启用行。
 */
@RestController
public class AdminModelConfigController {

    private final ObjectProvider<ModelConfigCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminModelConfigController(ObjectProvider<ModelConfigCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的模型配置。条数默认 50，上限 100。含停用。不读参数。
     */
    @GetMapping("/api/admin/model-configs")
    public ApiResult<List<ModelConfigSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增 1.0.0。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/model-configs")
    public ApiResult<ModelConfigSummary> register(@RequestBody ModelConfigRequest request) {
        ModelConfigRequest body = request == null
                ? new ModelConfigRequest(null, null, null, null)
                : request;
        return ApiResult.ok(catalog().register(body.configId(), body.model(), body.provider(), body.tenantScope()));
    }

    /**
     * 停用仍启用的 1.0.0。对话不再选用它。
     */
    @PostMapping("/api/admin/model-configs/{configId}/disable")
    public ApiResult<ModelConfigSummary> disable(@PathVariable String configId) {
        return ApiResult.ok(catalog().disable(configId));
    }

    /**
     * 恢复已停用的 1.0.0。
     */
    @PostMapping("/api/admin/model-configs/{configId}/restore")
    public ApiResult<ModelConfigSummary> restore(@PathVariable String configId) {
        return ApiResult.ok(catalog().restore(configId));
    }

    /** MySQL 关闭时没有目录。 */
    private ModelConfigCatalog catalog() {
        ModelConfigCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "model config is unavailable");
        }
        return catalog;
    }
}
