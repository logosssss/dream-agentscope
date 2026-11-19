package com.zhu.admin.api.approval;

import java.util.List;

/**
 * 用户待办。MySQL 关闭时没有这个实现。
 * 只支持 CONFIRM / TASK；APPROVAL 类型已硬删，见 docs/approval-hard-delete-落地.md。
 */
public interface UserTodoCatalog {

    /**
     * 给当前用户开一条 CONFIRM 或 TASK。同一用户、类型和关联再开一次是冲突。
     * 不核对话、运行或任务是否存在。不写截止时间。不接受 APPROVAL。
     */
    UserTodoRow open(String userId, String todoType, String refId, String title);

    /**
     * 当前用户把自己开着的确认或任务改成 DONE。
     * 别人的行按找不到处理。
     */
    UserTodoRow complete(String userId, Long todoId);

    /**
     * 当前用户把自己开着的确认或任务改成 CANCELLED。
     * 别人的行按找不到处理。
     */
    UserTodoRow cancel(String userId, Long todoId);

    /**
     * 这个用户未完成的 CONFIRM / TASK。条数默认 50，最多 100。用户为空时拒绝。
     */
    List<UserTodoRow> listOpen(String userId, int limit);

    /**
     * 这个用户的一条待办，含已关闭行。别人的主键按找不到处理。
     * 不带回截止时间。
     */
    UserTodoDetail ownedOne(String userId, Long todoId);
}
