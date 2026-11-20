package com.zhu.admin.api.controller.tool;

import com.zhu.admin.api.dto.tool.ToolRegistryRequest;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.tool.ToolRegistryCatalog;
import com.zhu.semantic.api.tool.ToolRegistrySummary;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记、停用、恢复和查看工具目录。不执行工具，也不写权限规则。
 */
@RestController
public class AdminToolRegistryController {

    private final ObjectProvider<ToolRegistryCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminToolRegistryController(ObjectProvider<ToolRegistryCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的工具目录。条数默认 50，上限 100。含停用。不读参数结构。
     */
    @GetMapping("/api/admin/tool-registries")
    public ApiResult<List<ToolRegistrySummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增 1.0.0。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tool-registries")
    public ApiResult<ToolRegistrySummary> register(@RequestBody ToolRegistryRequest request) {
        ToolRegistryRequest body = request == null
                ? new ToolRegistryRequest(null, null, null, null)
                : request;
        return ApiResult.ok(catalog().register(body.toolId(), body.name(), body.module(), body.description()));
    }

    /**
     * 停用仍启用的目录行。运行层 listEnabled 不再返回它。
     */
    @PostMapping("/api/admin/tool-registries/{toolId}/disable")
    public ApiResult<ToolRegistrySummary> disable(@PathVariable String toolId) {
        return ApiResult.ok(catalog().disable(toolId));
    }

    /**
     * 恢复已停用的目录行。
     */
    @PostMapping("/api/admin/tool-registries/{toolId}/restore")
    public ApiResult<ToolRegistrySummary> restore(@PathVariable String toolId) {
        return ApiResult.ok(catalog().restore(toolId));
    }

    /** MySQL 关闭时没有目录。 */
    private ToolRegistryCatalog catalog() {
        ToolRegistryCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tool registry is unavailable");
        }
        return catalog;
    }
}
