package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalRun;

/**
 * eval_run 的访问。登记用 BaseMapper 插入，不在这里改通过率。
 */
public interface EvalRunMapper extends BaseMapper<EvalRun> {
}
