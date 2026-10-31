package com.zhu.semantic.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * tool_registry 的访问。查询在 XML 里，Java 不写 SQL。
 */
@Mapper
public interface ToolRegistryMapper extends BaseMapper<ToolRegistry> {

    /**
     * 仍启用的工具，按标识排序。停用的不返回。
     */
    List<ToolRegistry> listEnabled();
}
