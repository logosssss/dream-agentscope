package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;

/**
 * eval_dataset 的访问。登记用 BaseMapper 插入，不在这里查询用例。
 */
public interface EvalDatasetMapper extends BaseMapper<EvalDataset> {
}
