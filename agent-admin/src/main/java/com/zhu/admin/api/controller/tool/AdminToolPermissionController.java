package com.zhu.admin.api.controller.tool;

import com.zhu.admin.api.dto.tool.ToolPermissionPriorityRequest;
import com.zhu.admin.api.dto.tool.ToolPermissionRequest;
import com.zhu.admin.api.dto.tool.ToolPermissionScopeRequest;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.tool.ToolPermissionCatalog;
import com.zhu.semantic.api.tool.ToolPermissionSummary;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记、停用、恢复和改优先级工具权限规则。不执行工具，也不解释条件。
 */
@RestController
public class AdminToolPermissionController {

    private final ObjectProvider<ToolPermissionCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminToolPermissionController(ObjectProvider<ToolPermissionCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的权限规则。条数默认 50，上限 100。含停用。
     */
    @GetMapping("/api/admin/tool-permissions")
    public ApiResult<List<ToolPermissionSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增一条。同一工具+租户+角色再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tool-permissions")
    public ApiResult<ToolPermissionSummary> register(@RequestBody ToolPermissionRequest request) {
        ToolPermissionRequest body = request == null
                ? new ToolPermissionRequest(null, null, null, null, null)
                : request;
        return ApiResult.ok(catalog().register(
                body.toolId(), body.tenantId(), body.roleId(), body.decision(), body.priority()));
    }

    /**
     * 停用一条仍启用的规则。空白租户/角色按全局。
     */
    @PostMapping("/api/admin/tool-permissions/{toolId}/disable")
    public ApiResult<ToolPermissionSummary> disable(
            @PathVariable String toolId, @RequestBody ToolPermissionScopeRequest request) {
        ToolPermissionScopeRequest body = request == null
                ? new ToolPermissionScopeRequest(null, null)
                : request;
        return ApiResult.ok(catalog().disable(toolId, body.tenantId(), body.roleId()));
    }

    /**
     * 恢复一条已停用的规则。空白租户/角色按全局。
     */
    @PostMapping("/api/admin/tool-permissions/{toolId}/restore")
    public ApiResult<ToolPermissionSummary> restore(
            @PathVariable String toolId, @RequestBody ToolPermissionScopeRequest request) {
        ToolPermissionScopeRequest body = request == null
                ? new ToolPermissionScopeRequest(null, null)
                : request;
        return ApiResult.ok(catalog().restore(toolId, body.tenantId(), body.roleId()));
    }

    /**
     * 改优先级。不改决定与启用。空白租户/角色按全局。
     */
    @PostMapping("/api/admin/tool-permissions/{toolId}/priority")
    public ApiResult<ToolPermissionSummary> updatePriority(
            @PathVariable String toolId, @RequestBody ToolPermissionPriorityRequest request) {
        ToolPermissionPriorityRequest body = request == null
                ? new ToolPermissionPriorityRequest(null, null, null)
                : request;
        return ApiResult.ok(catalog().updatePriority(
                toolId, body.tenantId(), body.roleId(), body.priority()));
    }

    /** MySQL 关闭时没有目录。 */
    private ToolPermissionCatalog catalog() {
        ToolPermissionCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tool permission is unavailable");
        }
        return catalog;
    }
}
