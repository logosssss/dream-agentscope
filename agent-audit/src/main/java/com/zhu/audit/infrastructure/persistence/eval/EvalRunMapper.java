package com.zhu.audit.infrastructure.persistence.eval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.eval.EvalRun;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/**
 * eval_run 的访问。登记用 BaseMapper 插入，查找和打分在 XML 里。
 */
public interface EvalRunMapper extends BaseMapper<EvalRun> {

    /**
     * 按执行标识取一行。没有时返回 null。
     */
    EvalRun findByEvalRunId(@Param("evalRunId") String evalRunId);

    /**
     * 只给仍在 RUNNING 的执行写入通过数、通过率和结束时间。返回实际更新行数。
     */
    int scoreRunning(
            @Param("evalRunId") String evalRunId,
            @Param("passedCases") int passedCases,
            @Param("passRate") BigDecimal passRate,
            @Param("finishedAt") LocalDateTime finishedAt);
}
