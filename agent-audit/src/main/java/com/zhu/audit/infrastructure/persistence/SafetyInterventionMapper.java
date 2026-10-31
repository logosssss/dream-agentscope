package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.SafetyIntervention;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * safety_intervention 的访问。追加用 BaseMapper 插入。最近列表在 XML 里。
 */
public interface SafetyInterventionMapper extends BaseMapper<SafetyIntervention> {

    /**
     * 最近的干预。按创建时间倒序。
     */
    List<SafetyIntervention> recent(@Param("limit") int limit);
}
