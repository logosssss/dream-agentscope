package com.zhu.admin.api.controller.event;

import com.zhu.audit.api.event.AuditEventRow;
import com.zhu.audit.api.event.AuditQuery;
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
     * 最近的审计事件。MySQL 关闭时为空列表。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/audits")
    public ApiResult<List<AuditEventRow>> audits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(audits.recentEvents(limit));
    }
}
