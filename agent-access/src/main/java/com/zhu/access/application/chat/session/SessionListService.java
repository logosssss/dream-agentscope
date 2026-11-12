package com.zhu.access.application.chat.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.access.api.dto.chat.ChatMessageItem;
import com.zhu.access.api.dto.chat.SessionDetail;
import com.zhu.access.api.dto.chat.SessionSummary;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 会话列表。事实在 MySQL，Redis 缓存五分钟。缓存失败就查库，不改用内存。
 */
@Service
public class SessionListService implements SessionIndex {

    /** 指南里的过期时间。写入后删键，下一轮重新查库。 */
    static final Duration TTL = Duration.ofMinutes(5);

    private static final TypeReference<List<SessionSummary>> LIST = new TypeReference<>() {
    };

    private final SessionMetaStore store;

    private final AccessCache cache;

    private final ChatMessageStore chatMessages;

    private final ObjectMapper json = new ObjectMapper();

    /** 存储和缓存都由启动模块注入。测试关掉 MySQL 时存储是空实现。 */
    public SessionListService(SessionMetaStore store, AccessCache cache, ChatMessageStore chatMessages) {
        this.store = store;
        this.cache = cache;
        this.chatMessages = chatMessages;
    }

    /**
     * 先落库再删缓存。删缓存失败不抛出，避免挡住对话；过期后列表会自己更新。
     * 标题只留一行里的前 40 个字符，换行压成空格。
     */
    @Override
    public void record(String tenantId, String userId, String sessionId, String channel, String text) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String tenant = tenantId == null ? "" : tenantId;
        store.touch(tenant, userId, sessionId, channel, title(text));
        try {
            cache.delete(cacheKey(tenant, userId));
        } catch (BusinessException error) {
            if (error.errorCode() != ErrorCode.UNAVAILABLE) {
                throw error;
            }
        }
    }

    /** 运行标识为空时跳过。列表不含这个字段，不必清列表缓存。详情走库。 */
    @Override
    public void attachRun(String tenantId, String userId, String sessionId, String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        String tenant = tenantId == null ? "" : tenantId;
        store.attachRun(tenant, userId, sessionId, runId);
    }

    /**
     * 先读缓存。没有、损坏，或 Redis 不可用时查库。查库之后尽量回填，回填失败仍返回库里的结果。
     */
    public List<SessionSummary> list(String tenantId, String userId) {
        String tenant = tenantId == null ? "" : tenantId;
        String key = cacheKey(tenant, userId);
        String cached = readCache(key);
        if (cached != null) {
            List<SessionSummary> parsed = parse(cached);
            if (parsed != null) {
                return parsed;
            }
        }
        List<SessionSummary> rows = store.list(tenant, userId);
        writeCache(key, rows);
        return rows;
    }

    /**
     * 空用户或空会话拒绝。找不到或不是本人按找不到。
     * 不走列表缓存，避免把最近运行挡住。
     */
    public SessionDetail get(String tenantId, String userId, String sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "session query is invalid");
        }
        String tenant = tenantId == null ? "" : tenantId;
        SessionDetail detail = store.findOwned(tenant, userId, sessionId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "session not found");
        }
        return detail;
    }

    /**
     * 先确认本人会话，再按 seq 升序拉气泡。limit 由仓储夹紧。
     */
    public List<ChatMessageItem> messages(String tenantId, String userId, String sessionId, int limit) {
        get(tenantId, userId, sessionId);
        return chatMessages.list(tenantId, userId, sessionId, limit);
    }

    /** 键带租户，避免不同租户的同名用户共用一份列表。冒号换成下划线。 */
    static String cacheKey(String tenantId, String userId) {
        return "session-list:" + segment(tenantId) + ":" + segment(userId);
    }

    /** 空白租户用减号，和额度键同一规则。 */
    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(":", "_");
    }

    /** 读失败视为未命中，接着查库。 */
    private String readCache(String key) {
        try {
            return cache.get(key);
        } catch (BusinessException error) {
            if (error.errorCode() == ErrorCode.UNAVAILABLE) {
                return null;
            }
            throw error;
        }
    }

    /** 回填失败不影响这次返回。 */
    private void writeCache(String key, List<SessionSummary> rows) {
        try {
            cache.put(key, json.writeValueAsString(rows), TTL);
        } catch (BusinessException error) {
            if (error.errorCode() != ErrorCode.UNAVAILABLE) {
                throw error;
            }
        } catch (JsonProcessingException ex) {
            // 序列化失败只少一次缓存，列表仍然来自库。
        }
    }

    /** 缓存不是合法 JSON 时返回 null，调用方改查库。 */
    private List<SessionSummary> parse(String raw) {
        try {
            return json.readValue(raw, LIST);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /** 空输入得到空标题。超过 40 个字符截断，避免把整段对话放进列表。跨包给 ChatAppService 写运行摘要用。 */
    public static String title(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (flat.length() <= 40) {
            return flat;
        }
        return flat.substring(0, 40);
    }
}
