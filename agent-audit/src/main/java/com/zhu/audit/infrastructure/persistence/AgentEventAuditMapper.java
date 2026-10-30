package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.AgentEventAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_event_audit 的 Mapper。简单插入用 BaseMapper，字段映射在同名 XML 里。
 */
@Mapper
public interface AgentEventAuditMapper extends BaseMapper<AgentEventAudit> {
}
