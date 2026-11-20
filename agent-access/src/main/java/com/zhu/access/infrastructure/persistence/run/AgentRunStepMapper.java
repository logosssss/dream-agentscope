package com.zhu.access.infrastructure.persistence.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.run.AgentRunStep;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_run_step 的访问。批量插入和按运行列出在 XML 里，Java 不写 SQL。
 */
public interface AgentRunStepMapper extends BaseMapper<AgentRunStep> {

    /**
     * 按给定序号写入同一次运行的步骤。调用方保证序号从 1 递增。
     */
    int insertSteps(@Param("runId") String runId, @Param("steps") List<AgentRunStep> steps);

    /**
     * 同一次运行的步骤。调用方先确认运行属于当前用户，这里不再按用户过滤。
     */
    List<AgentRunStep> listByRunId(@Param("runId") String runId);
}
