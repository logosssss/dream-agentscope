package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlFeedbackRecorder;
import com.zhu.audit.infrastructure.persistence.UserFeedbackMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserFeedback;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 用户反馈只接受 1 到 5。不连接 MySQL，也不核对该运行是否存在。
 */
class FeedbackRecorderTest {

    /** 空说明留空。评分超出范围时不插入。 */
    @Test
    void recordsARatingBetweenOneAndFive() {
        UserFeedbackMapper feedback = mock(UserFeedbackMapper.class);
        MysqlFeedbackRecorder recorder = new MysqlFeedbackRecorder(feedback);

        recorder.record("u", " ", 5, " ");
        ArgumentCaptor<UserFeedback> saved = ArgumentCaptor.forClass(UserFeedback.class);
        verify(feedback).insert(saved.capture());
        UserFeedback row = saved.getValue();
        assertThat(row.getUserId()).isEqualTo("u");
        assertThat(row.getRunId()).isNull();
        assertThat(row.getRating()).isEqualTo(5);
        assertThat(row.getComment()).isNull();

        assertThatThrownBy(() -> recorder.record("u", "run-1", 0, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(feedback, times(1)).insert(saved.capture());
    }
}
