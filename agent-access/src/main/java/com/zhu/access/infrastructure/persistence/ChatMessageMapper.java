package com.zhu.access.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.ChatMessage;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * chat_message 的访问。插入和列表在 XML 里。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /** 当前会话最大序号。没有行时返回 0。 */
    long maxSeq(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId);

    /** 插入一条气泡。 */
    int insertMessage(ChatMessage row);

    /** 本人本租户本会话按 seq 升序。 */
    List<ChatMessage> listOwned(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("limit") int limit);
}
