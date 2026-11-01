package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.infrastructure.persistence.EpisodicMemoryMapper;
import com.zhu.state.infrastructure.persistence.MysqlEpisodicMemoryRepository;
import com.zhu.state.infrastructure.persistence.MysqlUserProfileRepository;
import com.zhu.state.infrastructure.persistence.UserProfileMapper;
import com.zhu.state.infrastructure.persistence.entity.EpisodicMemory;
import com.zhu.state.infrastructure.persistence.entity.UserProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记忆只按用户写入短摘要。不连接 MySQL，也不在测试里拼 SQL。
 */
class CompanionMemoryRepositoryTest {

    /** 空白租户和会话写成空串。写入成功回自增 id。重要程度超出范围时不插入。 */
    @Test
    void memoryAppendsShortSummary() {
        EpisodicMemoryMapper mapper = mock(EpisodicMemoryMapper.class);
        when(mapper.insert(any(EpisodicMemory.class))).thenAnswer(invocation -> {
            EpisodicMemory row = invocation.getArgument(0);
            row.setId(9L);
            return 1;
        });
        MysqlEpisodicMemoryRepository memories = new MysqlEpisodicMemoryRepository(mapper);

        assertThat(memories.append(" ", "u", " ", "今天聊了散步", 3)).isEqualTo(9L);
        ArgumentCaptor<EpisodicMemory> row = ArgumentCaptor.forClass(EpisodicMemory.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getTenantId()).isEmpty();
        assertThat(row.getValue().getUserId()).isEqualTo("u");
        assertThat(row.getValue().getSessionId()).isEmpty();
        assertThat(row.getValue().getSummary()).isEqualTo("今天聊了散步");
        assertThat(row.getValue().getSalience()).isEqualTo(3);

        assertThatThrownBy(() -> memories.append("t", "u", "s", "摘要", 0))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(mapper).insert(any(EpisodicMemory.class));
    }

    /** 同一用户覆盖阶段。未登记的阶段不写。 */
    @Test
    void profileRejectsUnknownStage() {
        UserProfileMapper mapper = mock(UserProfileMapper.class);
        MysqlUserProfileRepository profiles = new MysqlUserProfileRepository(mapper);

        profiles.upsert(" ", "u", "ACQUAINTANCE", " ");
        ArgumentCaptor<UserProfile> row = ArgumentCaptor.forClass(UserProfile.class);
        verify(mapper).upsert(row.capture());
        assertThat(row.getValue().getTenantId()).isEmpty();
        assertThat(row.getValue().getUserId()).isEqualTo("u");
        assertThat(row.getValue().getRelationshipStage()).isEqualTo("ACQUAINTANCE");
        assertThat(row.getValue().getNotes()).isNull();

        assertThatThrownBy(() -> profiles.upsert("t", "u", "LOVER", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(mapper, never()).upsert(org.mockito.ArgumentMatchers.argThat(
                profile -> "LOVER".equals(profile.getRelationshipStage())));
    }

    /** 最近几条按调用方给的条数去查。用户为空不碰 Mapper。 */
    @Test
    void memoryRecentUsesLimit() {
        EpisodicMemoryMapper mapper = mock(EpisodicMemoryMapper.class);
        EpisodicMemory row = new EpisodicMemory();
        row.setId(4L);
        row.setTenantId("");
        row.setUserId("u");
        row.setSummary("散步");
        row.setSalience(2);
        when(mapper.recent("", "u", 3)).thenReturn(List.of(row));
        MysqlEpisodicMemoryRepository memories = new MysqlEpisodicMemoryRepository(mapper);

        assertThat(memories.recent(" ", "u", 3)).extracting(item -> item.id()).containsExactly(4L);
        assertThat(memories.recent("", " ", 3)).isEmpty();
        verify(mapper, never()).recent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(" "), org.mockito.ArgumentMatchers.anyInt());
    }

    /** 空 id 列表不查库。 */
    @Test
    void memoryFindByIdsSkipsBlank() {
        EpisodicMemoryMapper mapper = mock(EpisodicMemoryMapper.class);
        MysqlEpisodicMemoryRepository memories = new MysqlEpisodicMemoryRepository(mapper);

        assertThat(memories.findByIds("t", "u", List.of())).isEmpty();
        verify(mapper, never()).findByIds(any(), any(), any());
    }
}
