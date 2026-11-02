package com.zhu.semantic.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * tool_permission 的 Mapper。查询在同名 XML 里，Java 不写 SQL。
 */
@Mapper
public interface ToolPermissionMapper extends BaseMapper<ToolPermission> {

    /**
     * 只取这个工具仍启用的规则。条件列原样带回，由调用方解释时间窗。
     */
    List<ToolPermission> selectEnabled(@Param("toolId") String toolId);
}
