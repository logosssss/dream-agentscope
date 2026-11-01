package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.EpisodicMemory;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * episodic_memory 的访问。简单插入用 BaseMapper，最近和按 id 查在 XML 里。
 */
public interface EpisodicMemoryMapper extends BaseMapper<EpisodicMemory> {

    /**
     * 同一租户用户最近几条。条数由调用方收口后再传入。
     */
    List<EpisodicMemory> recent(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("limit") int limit);

    /**
     * 按主键取同一身份范围内的行。id 列表由调用方保证非空。
     */
    List<EpisodicMemory> findByIds(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("ids") List<Long> ids);
}
