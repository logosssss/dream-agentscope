package com.zhu.access.api.controller.account;

import com.zhu.access.api.dto.FeedbackRequest;
import com.zhu.access.application.auth.AccessAccountGate;
import com.zhu.audit.api.FeedbackDetail;
import com.zhu.audit.api.FeedbackRecorder;
import com.zhu.audit.api.FeedbackSummary;
import com.zhu.common.context.UserContext;
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
 * 当前用户提交并查看对一次运行的评分。不接收文件，也不核对运行是否存在。
 */
@RestController
public class FeedbackController {

    private final ObjectProvider<FeedbackRecorder> recorders;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有写入。接口仍在，调用会返回不可用。 */
    public FeedbackController(ObjectProvider<FeedbackRecorder> recorders, AccessAccountGate accounts) {
        this.recorders = recorders;
        this.accounts = accounts;
    }

    /**
     * 用户取请求头。没有用户或账号不可用时拒绝。评分不合法由记录口拒绝。
     */
    @PostMapping("/api/feedback")
    public ApiResult<Void> record(@RequestBody FeedbackRequest request) {
        accounts.requireActive();
        FeedbackRecorder recorder = recorders.getIfAvailable();
        if (recorder == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "feedback recorder is unavailable");
        }
        FeedbackRequest body = request == null ? new FeedbackRequest(null, null, null) : request;
        recorder.record(UserContext.get(), body.runId(), body.rating(), body.comment());
        return ApiResult.ok(null);
    }

    /**
     * 只返回这次请求头里的用户。条数默认 50，最多 100。
     * 表没有租户列，不按租户再滤，也不返回别人的行。
     */
    @GetMapping("/api/feedback")
    public ApiResult<List<FeedbackSummary>> list(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        FeedbackRecorder recorder = recorders.getIfAvailable();
        if (recorder == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "feedback recorder is unavailable");
        }
        return ApiResult.ok(recorder.owned(UserContext.get(), limit));
    }

    /**
     * 别人的主键按找不到处理。带创建时间，不读对话正文。
     */
    @GetMapping("/api/feedback/{feedbackId}")
    public ApiResult<FeedbackDetail> get(@PathVariable Long feedbackId) {
        accounts.requireActive();
        FeedbackRecorder recorder = recorders.getIfAvailable();
        if (recorder == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "feedback recorder is unavailable");
        }
        return ApiResult.ok(recorder.ownedOne(UserContext.get(), feedbackId));
    }
}
