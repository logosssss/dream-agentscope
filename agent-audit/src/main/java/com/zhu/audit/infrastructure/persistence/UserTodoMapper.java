package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserTodo;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * user_todo 的访问。新开用 BaseMapper 插入。未完成列表在 XML 里。
 */
public interface UserTodoMapper extends BaseMapper<UserTodo> {

    /**
     * 某一用户未完成的审批待办。调用方已经把条数收进 1 到 100。
     */
    List<UserTodo> openApprovals(@Param("userId") String userId, @Param("limit") int limit);
}
