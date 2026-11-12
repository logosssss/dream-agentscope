package com.zhu.access.api.controller.internal;

import com.zhu.access.api.dto.internal.WakeupRequest;
import com.zhu.access.application.chat.gateway.WakeupCoordinator;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户进程内部唤醒学习入口。须 {@code X-Wakeup-Token}；不在 agent-admin。
 * 见 docs/wakeup-落地.md。
 */
@RestController
public class WakeupController {

    private final WakeupCoordinator wakeups;

    public WakeupController(WakeupCoordinator wakeups) {
        this.wakeups = wakeups;
    }

    /**
     * 向进程内 Bus enqueue 一条唤醒。disabled 时 503；缺 sessionId 时 400。
     */
    @PostMapping("/api/internal/wakeups")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<Map<String, Object>> enqueue(@RequestBody WakeupRequest request) {
        if (!wakeups.isEnabled()) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "wakeup is disabled");
        }
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "sessionId is required");
        }
        Boolean accepted = wakeups
                .enqueue(request.userId(), request.sessionId(), request.agentId())
                .block();
        if (!Boolean.TRUE.equals(accepted)) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "wakeup is disabled");
        }
        return ApiResult.ok(Map.of(
                "queued", true,
                "sessionId", request.sessionId()));
    }
}
