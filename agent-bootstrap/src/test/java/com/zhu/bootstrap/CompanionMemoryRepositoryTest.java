package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.application.MysqlEpisodicMemoryRepository;
import com.zhu.state.application.MysqlUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 记忆只按用户写入短摘要。不连接 MySQL，也不走接入层。
 */
class CompanionMemoryRepositoryTest {

    /** 空白租户和会话写成空串。重要程度超出范围时不插入。 */
    @Test
    void memoryAppendsShortSummary() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MysqlEpisodicMemoryRepository memories = new MysqlEpisodicMemoryRepository(jdbc);

        memories.append(" ", "u", " ", "今天聊了散步", 3);
        verify(jdbc).update(anyString(), eq(""), eq("u"), eq(""), eq("今天聊了散步"), eq(3));

        assertThatThrownBy(() -> memories.append("t", "u", "s", "摘要", 0))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(jdbc, never()).update(anyString(), eq("t"), eq("u"), eq("s"), eq("摘要"), eq(0));
    }

    /** 同一用户覆盖阶段。未登记的阶段不写。 */
    @Test
    void profileRejectsUnknownStage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MysqlUserProfileRepository profiles = new MysqlUserProfileRepository(jdbc);

        profiles.upsert(" ", "u", "ACQUAINTANCE", " ");
        verify(jdbc).update(anyString(), eq(""), eq("u"), eq("ACQUAINTANCE"), eq(null));

        assertThatThrownBy(() -> profiles.upsert("t", "u", "LOVER", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(jdbc, never()).update(anyString(), eq("t"), eq("u"), eq("LOVER"), eq(null));
    }
}
