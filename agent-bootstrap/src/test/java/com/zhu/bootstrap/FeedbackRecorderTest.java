package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlFeedbackRecorder;
import com.zhu.audit.infrastructure.persistence.UserFeedbackMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserFeedback;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    /** 只列出本人行。条数截到 100。 */
    @Test
    void ownedListsRowsForUser() {
        UserFeedbackMapper feedback = mock(UserFeedbackMapper.class);
        MysqlFeedbackRecorder recorder = new MysqlFeedbackRecorder(feedback);
        UserFeedback row = new UserFeedback();
        row.setId(9L);
        row.setUserId("u");
        row.setRunId("run-1");
        row.setRating(4);
        row.setComment("ok");
        when(feedback.listOwned("u", 50)).thenReturn(List.of(row));
        assertThat(recorder.owned("u", 0)).satisfies(list -> {
            assertThat(list).hasSize(1);
            assertThat(list.get(0).id()).isEqualTo(9L);
            assertThat(list.get(0).runId()).isEqualTo("run-1");
            assertThat(list.get(0).rating()).isEqualTo(4);
            assertThat(list.get(0).comment()).isEqualTo("ok");
        });
        verify(feedback).listOwned("u", 50);
        assertThat(recorder.owned("u", 200)).isEmpty();
        verify(feedback).listOwned("u", 100);
    }

    /** 空用户按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankUser() {
        UserFeedbackMapper feedback = mock(UserFeedbackMapper.class);
        MysqlFeedbackRecorder recorder = new MysqlFeedbackRecorder(feedback);
        assertThatThrownBy(() -> recorder.owned(" ", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(feedback, never()).listOwned(any(), anyInt());
    }

    /** 创建时间为空时用空串。不读对话正文。 */
    @Test
    void ownedOneReturnsRatingAndCreatedAt() {
        UserFeedbackMapper feedback = mock(UserFeedbackMapper.class);
        MysqlFeedbackRecorder recorder = new MysqlFeedbackRecorder(feedback);
        UserFeedback row = new UserFeedback();
        row.setId(9L);
        row.setRunId("run-1");
        row.setRating(4);
        row.setComment("ok");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 3));
        when(feedback.findOwned(9L, "u")).thenReturn(row);
        assertThat(recorder.ownedOne("u", 9L)).satisfies(detail -> {
            assertThat(detail.id()).isEqualTo(9L);
            assertThat(detail.runId()).isEqualTo("run-1");
            assertThat(detail.rating()).isEqualTo(4);
            assertThat(detail.comment()).isEqualTo("ok");
            assertThat(detail.createdAt()).isEqualTo("2026-11-02T01:02:03");
        });
        UserFeedback blank = new UserFeedback();
        blank.setId(10L);
        blank.setRating(1);
        when(feedback.findOwned(10L, "u")).thenReturn(blank);
        assertThat(recorder.ownedOne("u", 10L).createdAt()).isEqualTo("");
    }

    /** 别人的或没有的按找不到。空用户或非法主键不查库。 */
    @Test
    void ownedOneRejectsMissingOrBlank() {
        UserFeedbackMapper feedback = mock(UserFeedbackMapper.class);
        MysqlFeedbackRecorder recorder = new MysqlFeedbackRecorder(feedback);
        when(feedback.findOwned(11L, "u")).thenReturn(null);
        assertThatThrownBy(() -> recorder.ownedOne("u", 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> recorder.ownedOne("u", 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> recorder.ownedOne(" ", 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(feedback).findOwned(11L, "u");
    }
}
