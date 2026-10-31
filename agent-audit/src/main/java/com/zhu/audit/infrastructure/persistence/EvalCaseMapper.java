package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalCase;
import org.apache.ibatis.annotations.Param;

/**
 * eval_case 的访问。追加用 BaseMapper 插入。按数据集计条数在 XML 里。
 */
public interface EvalCaseMapper extends BaseMapper<EvalCase> {

    /**
     * 某一版本已有的用例条数。没有用例时是 0。
     */
    long countVersion(@Param("datasetId") String datasetId, @Param("version") String version);
}
