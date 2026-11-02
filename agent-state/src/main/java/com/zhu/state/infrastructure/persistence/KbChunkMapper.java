package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.KbChunk;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * kb_chunk 访问。SUCCESS 文档回表在 XML。
 */
@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    int deleteByDocVersion(@Param("docId") String docId, @Param("version") int version);

    List<KbChunk> findSuccessByIds(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("ids") List<String> ids);
}
