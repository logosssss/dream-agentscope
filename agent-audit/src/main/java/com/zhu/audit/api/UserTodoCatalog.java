package com.zhu.audit.api;

import java.util.List;

/**
 * 用户待办。MySQL 关闭时没有这个实现。
 * 这次只开审批待办，不把已有待办改成完成。
 */
public interface UserTodoCatalog {

    /**
     * 给发起人开一条 APPROVAL、OPEN。同一用户和审批单再开一次视为已经有了。
     * 用户或审批单为空时不写。写失败由调用方吞掉。
     */
    void openApproval(String userId, String approvalId, String title);

    /**
     * 这个用户未完成的审批待办。条数默认 50，最多 100。用户为空时拒绝。
     */
    List<UserTodoRow> openApprovals(String userId, int limit);
}
