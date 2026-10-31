package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalCase;

/**
 * eval_case 的访问。追加用 BaseMapper 插入，不在这里查询或执行。
 */
public interface EvalCaseMapper extends BaseMapper<EvalCase> {
}
