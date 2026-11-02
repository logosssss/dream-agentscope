package com.zhu.audit.api;

import java.util.List;

/**
 * 用户待办。MySQL 关闭时没有这个实现。
 * 审批待办由询问事件开、决定后关。确认和任务由当前用户关成 DONE 或 CANCELLED。
 */
public interface UserTodoCatalog {

    /**
     * 给发起人开一条 APPROVAL、OPEN。同一用户和审批单再开一次视为已经有了。
     * 用户或审批单为空时不写。写失败由调用方吞掉。
     */
    void openApproval(String userId, String approvalId, String title);

    /**
     * 给当前用户开一条 CONFIRM 或 TASK。同一用户、类型和关联再开一次是冲突。
     * 不核对话、运行或任务是否存在。不写截止时间。
     */
    UserTodoRow open(String userId, String todoType, String refId, String title);

    /**
     * 把这张审批待办从 OPEN 改成 DONE 或 CANCELLED。
     * 单号为空或没有开着的行时不写。关闭失败由调用方吞掉。
     */
    void closeApproval(String approvalId, String status);

    /**
     * 当前用户把自己开着的确认或任务改成 DONE。
     * 审批待办仍走决定接口。别人的行按找不到处理。
     */
    UserTodoRow complete(String userId, Long todoId);

    /**
     * 当前用户把自己开着的确认或任务改成 CANCELLED。
     * 审批待办仍走决定接口。别人的行按找不到处理。
     */
    UserTodoRow cancel(String userId, Long todoId);

    /**
     * 这个用户未完成的待办，含审批、确认和任务。条数默认 50，最多 100。用户为空时拒绝。
     */
    List<UserTodoRow> openApprovals(String userId, int limit);

    /**
     * 这个用户的一条待办，含已关闭行。别人的主键按找不到处理。
     * 不带回截止时间，也不带工具参数。
     */
    UserTodoDetail ownedOne(String userId, Long todoId);
}
