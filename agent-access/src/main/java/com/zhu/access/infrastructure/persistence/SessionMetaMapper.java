package com.zhu.access.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.SessionMeta;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_session_meta 的访问。插入和列表在 XML 里，Java 不写 SQL。
 */
public interface SessionMetaMapper extends BaseMapper<SessionMeta> {

    /**
     * 新建一行，或更新已有会话的通道、时间和轮数。
     */
    int touch(SessionMeta row);

    /**
     * 当前租户和用户的活动会话，最多 50 条。
     */
    List<SessionMeta> listActive(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /**
     * 把最近一次运行标识写到已有会话上。没有这行时不插入。
     */
    int attachRun(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("runId") String runId);
}
