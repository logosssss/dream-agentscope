package com.zhu.audit.infrastructure.persistence.feedback;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.feedback.UserFeedback;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * user_feedback 的访问。追加用 BaseMapper 插入。本人列表在 XML 里。
 */
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {

    /**
     * 本人最近的反馈，新的在前。条数由调用方截好。
     */
    List<UserFeedback> listOwned(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 这个用户的一条反馈。别人的或没有时返回 null。
     */
    UserFeedback findOwned(@Param("id") Long id, @Param("userId") String userId);
}
