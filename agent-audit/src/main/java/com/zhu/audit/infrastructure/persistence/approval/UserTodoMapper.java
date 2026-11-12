package com.zhu.audit.infrastructure.persistence.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.approval.UserTodo;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * user_todo 的访问。新开用 BaseMapper 插入。列表、详情和关闭在 XML 里。
 */
public interface UserTodoMapper extends BaseMapper<UserTodo> {

    /**
     * 某一用户未完成的待办。调用方已经把条数收进 1 到 100。
     */
    List<UserTodo> openApprovals(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 只关还开着的审批待办。没有行时返回 0。
     */
    int closeApproval(@Param("refId") String refId, @Param("status") String status);

    /**
     * 只关这个用户还开着的确认或任务。审批待办不动。状态由调用方写成 DONE 或 CANCELLED。
     */
    int closeOpen(@Param("id") Long id, @Param("userId") String userId, @Param("status") String status);

    /**
     * 这个用户的一条待办。别人的或没有时返回 null。含已关闭行。
     */
    UserTodo findOwned(@Param("id") Long id, @Param("userId") String userId);
}
