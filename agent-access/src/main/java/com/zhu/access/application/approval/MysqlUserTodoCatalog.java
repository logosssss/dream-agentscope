package com.zhu.access.application.approval;

import com.zhu.access.api.approval.UserTodoCatalog;
import com.zhu.access.api.approval.UserTodoDetail;
import com.zhu.access.api.approval.UserTodoRow;
import com.zhu.access.application.support.AccessQueryLimits;
import com.zhu.access.infrastructure.persistence.approval.UserTodoMapper;
import com.zhu.access.infrastructure.persistence.entity.approval.UserTodo;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的用户待办。只在存储开关打开时创建。
 * 只写/列 CONFIRM 与 TASK；APPROVAL 类型已硬删。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlUserTodoCatalog implements UserTodoCatalog {

    private static final Set<String> OPEN_TYPES = Set.of("CONFIRM", "TASK");

    private static final String OPEN = "OPEN";

    private static final String DONE = "DONE";

    private static final String CANCELLED = "CANCELLED";

    private static final int USER_LIMIT = 128;

    private static final int REF_LIMIT = 128;

    private static final int TITLE_LIMIT = 256;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserTodoMapper todos;

    /** 插入和列表用同一个 Mapper。重复的用户加类型加单号由唯一键拒绝。 */
    public MysqlUserTodoCatalog(UserTodoMapper todos) {
        this.todos = todos;
    }

    /**
     * 只收 CONFIRM 和 TASK。标题可空。同一用户、类型和关联再插入一次按冲突处理。
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

    /** 完成走同一条关闭路径，状态写成 DONE。 */
    @Override
    public UserTodoRow complete(String userId, Long todoId) {
        return closeOwn(userId, todoId, DONE);
    }

    /** 和完成同一条校验。只是把状态写成取消。 */
    @Override
    public UserTodoRow cancel(String userId, Long todoId) {
        return closeOwn(userId, todoId, CANCELLED);
    }

    /**
     * 先读再按开着的确认或任务更新。别人的主键按找不到处理。
     */
    private UserTodoRow closeOwn(String userId, Long todoId, String status) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT || todoId == null || todoId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "todo is invalid");
        }
        UserTodo existing = todos.selectById(todoId);
        if (existing == null || !userId.equals(existing.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "todo not found");
        }
        if (!OPEN_TYPES.contains(existing.getTodoType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "todo type cannot be completed here");
        }
        if (!OPEN.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "todo is not open");
        }
        int updated = todos.closeOpen(todoId, userId, status);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "todo is not open");
        }
        return new UserTodoRow(existing.getId(), existing.getTodoType(), existing.getRefId(), existing.getTitle(), status);
    }

    /**
     * 空用户拒绝。不按租户再滤；只列 CONFIRM / TASK。
     */
    @Override
    public List<UserTodoRow> listOpen(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        return todos.listOpen(clip(userId, USER_LIMIT), AccessQueryLimits.normalize(limit)).stream()
                .map(MysqlUserTodoCatalog::row)
                .toList();
    }

    /**
     * 空标识拒绝。找不到或不是本人按找不到。
     */
    @Override
    public UserTodoDetail ownedOne(String userId, Long todoId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT || todoId == null || todoId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "todo is invalid");
        }
        UserTodo row = todos.findOwned(todoId, userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "todo not found");
        }
        String created = row.getCreatedAt() == null ? "" : TIME.format(row.getCreatedAt());
        return new UserTodoDetail(
                row.getId(),
                row.getTodoType(),
                row.getRefId(),
                row.getTitle(),
                row.getStatus(),
                created);
    }

    private static UserTodoRow row(UserTodo todo) {
        return new UserTodoRow(todo.getId(), todo.getTodoType(), todo.getRefId(), todo.getTitle(), todo.getStatus());
    }

    private static String clip(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
