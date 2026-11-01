package com.zhu.audit.application;

import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.api.UserTodoRow;
import com.zhu.audit.infrastructure.persistence.UserTodoMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserTodo;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的用户待办。只在存储开关打开时创建。
 * 决定后关掉对应审批待办。确认和任务由当前用户关成 DONE。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlUserTodoCatalog implements UserTodoCatalog {

    private static final String APPROVAL = "APPROVAL";

    private static final Set<String> OPEN_TYPES = Set.of("CONFIRM", "TASK");

    private static final String OPEN = "OPEN";

    private static final String DONE = "DONE";

    private static final Set<String> CLOSE_STATUSES = Set.of("DONE", "CANCELLED");

    private static final int USER_LIMIT = 128;

    private static final int REF_LIMIT = 128;

    private static final int TITLE_LIMIT = 256;

    private final UserTodoMapper todos;

    /** 插入和列表用同一个 Mapper。重复的用户加类型加单号由唯一键拒绝。 */
    public MysqlUserTodoCatalog(UserTodoMapper todos) {
        this.todos = todos;
    }

    /**
     * 用户或审批单为空时不写。同一条再开一次当作已经有了，不抛冲突。
     * 标题只留工具名。超长字段截到列宽。
     */
    @Override
    public void openApproval(String userId, String approvalId, String title) {
        if (userId == null || userId.isBlank() || approvalId == null || approvalId.isBlank()) {
            return;
        }
        UserTodo row = new UserTodo();
        row.setUserId(clip(userId, USER_LIMIT));
        row.setTodoType(APPROVAL);
        row.setRefId(clip(approvalId, REF_LIMIT));
        row.setTitle(title == null || title.isBlank() ? null : clip(title, TITLE_LIMIT));
        row.setStatus(OPEN);
        try {
            todos.insert(row);
        } catch (DataIntegrityViolationException ex) {
            return;
        }
    }

    /**
     * 只收 CONFIRM 和 TASK。审批待办仍走询问事件，避免用户自己补一条。
     * 标题可空。同一用户、类型和关联再插入一次按冲突处理。
     */
    @Override
    public UserTodoRow open(String userId, String todoType, String refId, String title) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || todoType == null || todoType.isBlank() || !OPEN_TYPES.contains(todoType)
                || refId == null || refId.isBlank() || refId.length() > REF_LIMIT
                || (title != null && title.length() > TITLE_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "todo is invalid");
        }
        UserTodo row = new UserTodo();
        row.setUserId(userId);
        row.setTodoType(todoType);
        row.setRefId(refId);
        row.setTitle(title == null || title.isBlank() ? null : title);
        row.setStatus(OPEN);
        try {
            todos.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "todo already exists");
        }
        return row(row);
    }

    /**
     * 只收 DONE 和 CANCELLED。其它状态不改行，避免把待办写成未知值。
     */
    @Override
    public void closeApproval(String approvalId, String status) {
        if (approvalId == null || approvalId.isBlank() || status == null || !CLOSE_STATUSES.contains(status)) {
            return;
        }
        todos.closeApproval(clip(approvalId, REF_LIMIT), status);
    }

    /**
     * 先读再按开着的确认或任务更新，避免盖掉审批待办或已经关上的行。
     * 别人的主键按找不到处理，不把标题交出去。
     */
    @Override
    public UserTodoRow complete(String userId, Long todoId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT || todoId == null || todoId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "todo is invalid");
        }
        UserTodo existing = todos.selectById(todoId);
        if (existing == null || !userId.equals(existing.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "todo not found");
        }
        if (!OPEN_TYPES.contains(existing.getTodoType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "approval todo cannot be completed here");
        }
        if (!OPEN.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "todo is not open");
        }
        int updated = todos.completeOpen(todoId, userId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "todo is not open");
        }
        return new UserTodoRow(existing.getId(), existing.getTodoType(), existing.getRefId(), existing.getTitle(), DONE);
    }

    /**
     * 空用户拒绝。不按租户再滤一层，这张表没有租户列。
     */
    @Override
    public List<UserTodoRow> openApprovals(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        return todos.openApprovals(clip(userId, USER_LIMIT), AuditQueryLimits.normalize(limit)).stream()
                .map(MysqlUserTodoCatalog::row)
                .toList();
    }

    /** 列表不带用户列，调用方已经按这个用户查过。 */
    private static UserTodoRow row(UserTodo todo) {
        return new UserTodoRow(todo.getId(), todo.getTodoType(), todo.getRefId(), todo.getTitle(), todo.getStatus());
    }

    /** 列宽不够时只保留前一段。 */
    private static String clip(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
