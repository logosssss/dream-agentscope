package com.zhu.audit.infrastructure.persistence.approval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.approval.UserTodo;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * user_todo 访问。列表与关闭在 XML；插入走 BaseMapper。
 */
public interface UserTodoMapper extends BaseMapper<UserTodo> {

    /**
     * 这个用户还开着的 CONFIRM / TASK。
     */
    List<UserTodo> listOpen(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 当前用户关确认或任务。已经关上的行不动。
     */
    int closeOpen(@Param("id") Long id, @Param("userId") String userId, @Param("status") String status);

    /**
     * 本人一条待办，含已关闭。
     */
    UserTodo findOwned(@Param("id") Long id, @Param("userId") String userId);
}
