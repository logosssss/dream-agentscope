package com.zhu.state.application;

import com.zhu.state.api.UserProfileItem;
import com.zhu.state.api.UserProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时不写也不读用户画像。只给测试用，不改记到内存。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentUserProfileRepository implements UserProfileRepository {

    /** 没有表可写，直接跳过。 */
    @Override
    public void upsert(String tenantId, String userId, String relationshipStage, String notes) {
    }

    /** 没有表可查，返回空。 */
    @Override
    public UserProfileItem find(String tenantId, String userId) {
        return null;
    }
}
