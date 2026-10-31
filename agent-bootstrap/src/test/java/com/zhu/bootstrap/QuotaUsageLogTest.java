package com.zhu.bootstrap;

import com.zhu.access.application.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.audit.api.QuotaUsageWriter;
import com.zhu.audit.application.MysqlQuotaUsageWriter;
import com.zhu.audit.infrastructure.persistence.QuotaUsageLogMapper;
import com.zhu.audit.infrastructure.persistence.entity.QuotaUsageLog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 每日请求通过后的流水。不连接 MySQL 或 Redis。超限和重复提交不记第二次。
 */
class QuotaUsageLogTest {

    /** 第一次通过才记。同一请求标识再来一次不算新的消耗。 */
    @Test
    void onlyAcceptedDailyRequestsAreLogged() {
        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setDailyLimit(1);
        QuotaUsageWriter writer = mock(QuotaUsageWriter.class);
        QuotaGuard guard = new QuotaGuard(new FakeAccessCache(), limits, Clock.systemUTC(), null, writer);

        guard.check("t", "u", "chat", "req-1");
        assertThatThrownBy(() -> guard.check("t", "u", "chat", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> guard.check("t", "u", "chat", "req-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
        verify(writer, times(1)).recordDailyRequest("t", "u", "chat");
    }

    /** 不限额时没有扣减，流水也不写。写入抛错时这次请求仍然通过。 */
    @Test
    void unlimitedSkipsTheLogAndWriteFailureIsIgnored() {
        AccessLimitProperties open = new AccessLimitProperties();
        QuotaUsageWriter writer = mock(QuotaUsageWriter.class);
        new QuotaGuard(new FakeAccessCache(), open, Clock.systemUTC(), null, writer)
                .check("t", "u", "chat", null);
        verifyNoInteractions(writer);

        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setDailyLimit(1);
        doThrow(new IllegalStateException("down")).when(writer).recordDailyRequest("t", "u", "chat");
        QuotaGuard guard = new QuotaGuard(new FakeAccessCache(), limits, Clock.systemUTC(), null, writer);
        assertThatCode(() -> guard.check("t", "u", "chat", null)).doesNotThrowAnyException();
    }

    /** 一行是一次请求。空用户和空原因留空，不填运行标识。 */
    @Test
    void writerInsertsOneRequest() {
        QuotaUsageLogMapper logs = mock(QuotaUsageLogMapper.class);
        MysqlQuotaUsageWriter writer = new MysqlQuotaUsageWriter(logs);

        writer.recordDailyRequest("t", " ", null);

        ArgumentCaptor<QuotaUsageLog> saved = ArgumentCaptor.forClass(QuotaUsageLog.class);
        verify(logs).insert(saved.capture());
        QuotaUsageLog row = saved.getValue();
        assertThat(row.getTenantId()).isEqualTo("t");
        assertThat(row.getUserId()).isNull();
        assertThat(row.getQuotaType()).isEqualTo("REQUESTS");
        assertThat(row.getAmount()).isEqualTo(1L);
        assertThat(row.getReason()).isNull();
    }
}
