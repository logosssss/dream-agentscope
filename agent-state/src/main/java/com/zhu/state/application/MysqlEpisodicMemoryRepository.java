package com.zhu.state.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.EpisodicMemoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 把情景记忆写入 MySQL。只在存储开关打开时创建。不改已有行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlEpisodicMemoryRepository implements EpisodicMemoryRepository {

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int SESSION_LIMIT = 128;

    private static final int SUMMARY_LIMIT = 500;

    private final JdbcTemplate jdbc;

    /** 写入走 JDBC。校验失败不写库。 */
    public MysqlEpisodicMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 空白租户和会话按空串。不截断超长字段。
     * 摘要必须是短句，不要把整段对话传进来。
     */
    @Override
    public void append(String tenantId, String userId, String sessionId, String summary, int salience) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || (sessionId != null && sessionId.length() > SESSION_LIMIT)
                || summary == null || summary.isBlank() || summary.length() > SUMMARY_LIMIT
                || salience < 1 || salience > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "episodic memory is invalid");
        }
        jdbc.update(
                "INSERT INTO episodic_memory (tenant_id, user_id, session_id, summary, salience) VALUES (?, ?, ?, ?, ?)",
                tenantId == null || tenantId.isBlank() ? "" : tenantId,
                userId,
                sessionId == null || sessionId.isBlank() ? "" : sessionId,
                summary,
                salience);
    }
}
