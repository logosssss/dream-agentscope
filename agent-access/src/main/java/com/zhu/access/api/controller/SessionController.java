package com.zhu.access.api.controller;

import com.zhu.access.api.dto.SessionSummary;
import com.zhu.access.application.SessionListService;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的会话列表。数据来自 MySQL，Redis 只缓存。
 */
@RestController
public class SessionController {

    private final SessionListService sessions;

    /** 列表按请求头里的租户和用户查询。 */
    public SessionController(SessionListService sessions) {
        this.sessions = sessions;
    }

    /**
     * 没有用户标识时拒绝，避免把别人的会话交出去。
     * Redis 不可用时服务会改查 MySQL，这里不另做降级。
     */
    @GetMapping("/api/sessions")
    public ApiResult<List<SessionSummary>> list() {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(sessions.list(tenantId, userId));
    }
}
