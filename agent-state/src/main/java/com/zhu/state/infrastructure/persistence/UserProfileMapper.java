package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.UserProfile;

/**
 * user_profile 的访问。覆盖写在 XML 里，Java 不写 SQL。
 */
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /**
     * 没有行就插入，有行就覆盖阶段和备注。
     */
    int upsert(UserProfile row);
}
