package com.zhu.semantic.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * tool_registry 的访问。查询在 XML 里，Java 不写 SQL。
 */
@Mapper
public interface ToolRegistryMapper extends BaseMapper<ToolRegistry> {

    /**
     * 仍启用的工具，按标识排序。停用的不返回。
     */
    List<ToolRegistry> listEnabled();

    /**
     * 最近登记的工具，含停用。不读 param_schema。
     */
    List<ToolRegistry> recent(@Param("limit") int limit);

    /**
     * 按工具标识取一行，含停用。
     */
    ToolRegistry findByToolId(@Param("toolId") String toolId);

    /**
     * 只把仍启用的行改成停用。返回影响行数。
     */
    int disableEnabled(@Param("toolId") String toolId);

    /**
     * 只把停用行改回启用。返回影响行数。
     */
    int restoreDisabled(@Param("toolId") String toolId);
}
