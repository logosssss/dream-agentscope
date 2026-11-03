package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * eval_dataset 的访问。登记用 BaseMapper 插入。查找、列表与停用恢复在 XML 里。
 */
public interface EvalDatasetMapper extends BaseMapper<EvalDataset> {

    /**
     * 某一版本是否已经登记。停用也返回。没有时返回 null。
     */
    EvalDataset findVersion(@Param("datasetId") String datasetId, @Param("version") String version);

    /**
     * 最近登记的评测数据集。调用方已经把条数收进 1 到 100。含停用。
     */
    List<EvalDataset> recent(@Param("limit") int limit);

    /**
     * 只把仍启用的指定版本改成停用。返回影响行数。
     */
    int disableEnabled(@Param("datasetId") String datasetId, @Param("version") String version);

    /**
     * 只把停用的指定版本改回启用。返回影响行数。
     */
    int restoreDisabled(@Param("datasetId") String datasetId, @Param("version") String version);
}
