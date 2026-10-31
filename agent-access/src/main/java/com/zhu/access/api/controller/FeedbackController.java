package com.zhu.access.api.controller;

import com.zhu.access.api.dto.FeedbackRequest;
import com.zhu.audit.api.FeedbackRecorder;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户提交对一次运行的评分。不接收文件，也不核对运行是否存在。
 */
@RestController
public class FeedbackController {

    private final ObjectProvider<FeedbackRecorder> recorders;

    /** MySQL 关闭时没有写入。接口仍在，调用会返回不可用。 */
    public FeedbackController(ObjectProvider<FeedbackRecorder> recorders) {
        this.recorders = recorders;
    }

    /**
     * 用户取请求头。没有用户时拒绝。评分不合法由记录口拒绝。
     */
    @PostMapping("/api/feedback")
    public ApiResult<Void> record(@RequestBody FeedbackRequest request) {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        FeedbackRecorder recorder = recorders.getIfAvailable();
        if (recorder == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "feedback recorder is unavailable");
        }
        FeedbackRequest body = request == null ? new FeedbackRequest(null, null, null) : request;
        recorder.record(userId, body.runId(), body.rating(), body.comment());
        return ApiResult.ok(null);
    }
}
