package com.zhu.access.api.controller;

import com.zhu.access.api.dto.SessionDetail;
import com.zhu.access.api.dto.SessionSummary;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.application.SessionListService;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的会话。列表走缓存；详情走库，仍不含正文。
 */
@RestController
public class SessionController {

    private final SessionListService sessions;

    private final AccessAccountGate accounts;

    /** 列表按请求头里的租户和用户查询。先过账号门。 */
    public SessionController(SessionListService sessions, AccessAccountGate accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    /**
     * 没有用户或账号不可用时拒绝，避免把别人的会话交出去。
     * Redis 不可用时服务会改查 MySQL，这里不另做降级。
     */
    @GetMapping("/api/sessions")
    public ApiResult<List<SessionSummary>> list() {
        accounts.requireActive();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(sessions.list(tenantId, UserContext.get()));
    }

    /**
     * 别人的会话按找不到处理。不读对话正文，也不走列表缓存。
     */
    @GetMapping("/api/sessions/{sessionId}")
    public ApiResult<SessionDetail> get(@PathVariable String sessionId) {
        accounts.requireActive();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(sessions.get(tenantId, UserContext.get(), sessionId));
    }
}
