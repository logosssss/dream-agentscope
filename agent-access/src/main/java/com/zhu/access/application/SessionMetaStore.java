package com.zhu.access.application;

import com.zhu.access.api.dto.SessionSummary;
import java.util.List;

/**
 * 会话元数据的存放处。列表的事实来源是这里，Redis 只做缓存。
 */
public interface SessionMetaStore {

    /**
     * 新建或更新一行。已有标题保持不变，轮数加一。
     */
    void touch(String tenantId, String userId, String sessionId, String channel, String title);

    /**
     * 当前租户和用户的活动会话，按最近活动倒序，最多 50 条。
     */
    List<SessionSummary> list(String tenantId, String userId);
}
