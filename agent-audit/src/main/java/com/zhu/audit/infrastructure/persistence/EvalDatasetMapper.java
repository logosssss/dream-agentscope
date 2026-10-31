package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;
import org.apache.ibatis.annotations.Param;

/**
 * eval_dataset 的访问。登记用 BaseMapper 插入。按标识和版本查找在 XML 里。
 */
public interface EvalDatasetMapper extends BaseMapper<EvalDataset> {

    /**
     * 某一版本是否已经登记。没有时返回 null。
     */
    EvalDataset findVersion(@Param("datasetId") String datasetId, @Param("version") String version);
}
