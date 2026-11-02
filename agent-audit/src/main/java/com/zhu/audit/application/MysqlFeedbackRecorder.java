package com.zhu.audit.application;

import com.zhu.audit.api.FeedbackRecorder;
import com.zhu.audit.api.FeedbackSummary;
import com.zhu.audit.infrastructure.persistence.UserFeedbackMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserFeedback;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把用户反馈写入 MySQL，并按用户列出。只在存储开关打开时创建。不改已有行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlFeedbackRecorder implements FeedbackRecorder {

    private static final int USER_LIMIT = 128;

    private static final int RUN_LIMIT = 128;

    private static final int COMMENT_LIMIT = 1000;

    private final UserFeedbackMapper feedback;

    /** 插入和列表用同一个 Mapper。校验失败不写库。 */
    public MysqlFeedbackRecorder(UserFeedbackMapper feedback) {
        this.feedback = feedback;
    }

    /**
     * 评分只接受 1 到 5。空说明留空。不截断超长字段，避免反馈对到别的运行。
     */
    @Override
    public void record(String userId, String runId, Integer rating, String comment) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || rating == null || rating < 1 || rating > 5
                || (runId != null && runId.length() > RUN_LIMIT)
                || (comment != null && comment.length() > COMMENT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "feedback is invalid");
        }
        UserFeedback row = new UserFeedback();
        row.setUserId(userId);
        row.setRunId(runId == null || runId.isBlank() ? null : runId);
        row.setRating(rating);
        row.setComment(comment == null || comment.isBlank() ? null : comment);
        feedback.insert(row);
    }

    /**
     * 空用户拒绝。表没有租户列，只按用户切开。
     */
    @Override
    public List<FeedbackSummary> owned(String userId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "feedback is invalid");
        }
        List<UserFeedback> rows = feedback.listOwned(userId, AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlFeedbackRecorder::summary).toList();
    }

    /** 列表不带回时间戳，调用方只需要评分和说明。 */
    private static FeedbackSummary summary(UserFeedback row) {
        return new FeedbackSummary(
                row.getId(), row.getUserId(), row.getRunId(), row.getRating(), row.getComment());
    }
}
