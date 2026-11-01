package com.zhu.vector.application;

import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorRecord;
import com.zhu.vector.api.VectorStore;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 启动依赖关闭时不连 Milvus。只给测试用，不改记到内存。
 */
@Service
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "false")
public class AbsentVectorStore implements VectorStore {

    /** 没有集合可写，直接跳过。 */
    @Override
    public void upsert(VectorRecord record) {
    }

    /** 没有集合可查，返回空列表。 */
    @Override
    public List<VectorHit> search(VectorQuery query) {
        return List.of();
    }

    /** 没有集合可删，直接跳过。 */
    @Override
    public void deleteById(String collection, String id) {
    }

    /** 没有集合可删，直接跳过。 */
    @Override
    public void deleteByScope(String collection, String tenantId, String userId, String agentId) {
    }
}
