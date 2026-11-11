package com.zhu.access.application.chat;

import com.zhu.access.api.dto.SessionDetail;
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

    /**
     * 记下最近一次运行。会话行还不存在时什么都不做。
     */
    void attachRun(String tenantId, String userId, String sessionId, String runId);

    /**
     * 同一租户下这个会话是否已属于别人。没有别人时为空。
     * 缺省不查，测试替身不用再写一遍。
     */
    default String otherUser(String tenantId, String sessionId, String userId) {
        return null;
    }

    /**
     * 当前用户在这个租户下的一次会话。没有或不属于本人时返回 null。
     * 缺省返回 null，测试替身不用再写一遍。
     */
    default SessionDetail findOwned(String tenantId, String userId, String sessionId) {
        return null;
    }
}
