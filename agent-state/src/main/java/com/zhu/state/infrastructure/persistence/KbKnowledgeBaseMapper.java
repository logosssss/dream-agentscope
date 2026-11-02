package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.KbKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * kb_knowledge_base 访问。按所有者查在 XML。
 */
@Mapper
public interface KbKnowledgeBaseMapper extends BaseMapper<KbKnowledgeBase> {

    /** 本人库。 */
    KbKnowledgeBase findOwned(
            @Param("kbId") String kbId, @Param("tenantId") String tenantId, @Param("userId") String userId);
}
