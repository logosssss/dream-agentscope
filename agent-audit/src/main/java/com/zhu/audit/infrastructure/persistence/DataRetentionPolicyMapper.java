package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataRetentionPolicy;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * data_retention_policy 的访问。登记用 BaseMapper 插入，列表在 XML 里。不在这里执行清理。
 */
public interface DataRetentionPolicyMapper extends BaseMapper<DataRetentionPolicy> {

    /**
     * 最近登记的保留策略。调用方已经把条数收进 1 到 100。含停用。
     */
    List<DataRetentionPolicy> recent(@Param("limit") int limit);
}
