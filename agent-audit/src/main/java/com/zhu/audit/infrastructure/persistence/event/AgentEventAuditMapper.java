package com.zhu.audit.infrastructure.persistence.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.event.AgentEventAudit;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * agent_event_audit 的 Mapper。简单插入用 BaseMapper，最近记录的查询在同名 XML 里。
 */
@Mapper
public interface AgentEventAuditMapper extends BaseMapper<AgentEventAudit> {

    /**
     * 按主键从新到旧取有限条。条数由调用方收口后再传入。
     */
    List<AgentEventAudit> recent(@Param("limit") int limit);
}
