package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.UserProfile;
import org.apache.ibatis.annotations.Param;

/**
 * user_profile 的访问。覆盖写和按身份查找都在 XML 里，Java 不写 SQL。
 */
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /**
     * 没有行就插入，有行就覆盖阶段和备注。
     */
    int upsert(UserProfile row);

    /**
     * 同一租户用户至多一行。没有时返回 null。
     */
    UserProfile find(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
