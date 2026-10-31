package com.zhu.access.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentRun;

/**
 * agent_run 的访问。插入在 XML 里，Java 不写 SQL。
 */
public interface AgentRunMapper extends BaseMapper<AgentRun> {

    /**
     * 写入一次已经结束的运行。不更新中途状态。
     */
    int insertFinished(AgentRun row);
}
