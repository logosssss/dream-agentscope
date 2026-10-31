package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserFeedback;

/**
 * user_feedback 的访问。追加用 BaseMapper 插入，不在这里查询。
 */
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {
}
