package com.zhu.audit.infrastructure.persistence.retention;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.retention.DataRetentionPolicy;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * data_retention_policy 的访问。登记用 BaseMapper 插入，列表与停用恢复在 XML 里。不在这里执行清理。
 */
public interface DataRetentionPolicyMapper extends BaseMapper<DataRetentionPolicy> {

    /**
     * 最近登记的保留策略。调用方已经把条数收进 1 到 100。含停用。
     */
    List<DataRetentionPolicy> recent(@Param("limit") int limit);

    /**
     * 按数据类型和范围取一行。停用也返回。
     */
    DataRetentionPolicy findByKey(@Param("dataType") String dataType, @Param("scope") String scope);

    /**
     * 只把仍启用的那一行改成停用。返回更新行数。
     */
    int disableEnabled(@Param("dataType") String dataType, @Param("scope") String scope);

    /**
     * 只把已停用的那一行改成启用。返回更新行数。
     */
    int restoreDisabled(@Param("dataType") String dataType, @Param("scope") String scope);
}
