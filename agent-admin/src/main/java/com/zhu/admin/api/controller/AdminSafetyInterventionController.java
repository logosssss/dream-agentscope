package com.zhu.admin.api.controller;

import com.zhu.audit.api.SafetyInterventionCatalog;
import com.zhu.audit.api.SafetyInterventionSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程查看安全干预。只读最近记录，不改档位。
 */
@RestController
public class AdminSafetyInterventionController {

    private final ObjectProvider<SafetyInterventionCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminSafetyInterventionController(ObjectProvider<SafetyInterventionCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近的干预。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/safety-interventions")
    public ApiResult<List<SafetyInterventionSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        SafetyInterventionCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "safety intervention is unavailable");
        }
        return ApiResult.ok(catalog.recent(limit));
    }
}
