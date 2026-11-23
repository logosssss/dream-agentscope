package com.zhu.admin.api.controller.event;

import com.zhu.admin.api.event.AuditEventRow;
import com.zhu.admin.api.event.AuditQuery;
import com.zhu.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程上的事件审计查询。只读审计模块，不读用户进程内存。
 */
@RestController
public class AdminAuditController {

    private final AuditQuery audits;

    /** 注入只读查询口。 */
    public AdminAuditController(AuditQuery audits) {
        this.audits = audits;
    }

    /**
     * 审计事件。三个过滤参数都可选：运维报障时一般先按运行号查，其次按会话或用户。
     * MySQL 关闭时为空列表。条数默认 50，上限 100。
     *
     * @param limit     条数
     * @param userId    操作者
     * @param sessionId 会话
     * @param runId     运行标识
     */
    @GetMapping("/api/admin/audits")
    public ApiResult<List<AuditEventRow>> audits(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String runId) {
        return ApiResult.ok(audits.events(userId, sessionId, runId, limit));
    }
}
