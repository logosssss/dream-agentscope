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

    /**
     * 仍启用、租户与角色均为 {@code *}、且 conditions 空白的规则。
     * 给引擎 PermissionContext 注入；有条件或写明范围的行不在这里。
     */
    List<ToolPermission> selectEnabledGlobalUnconditional();

    /**
     * 最近登记的规则，含停用。管理列表用，不按工具再滤。
     */
    List<ToolPermission> recent(@Param("limit") int limit);

    /**
     * 按工具+租户+角色取一行，含停用。
     */
    ToolPermission findByKey(
            @Param("toolId") String toolId,
            @Param("tenantId") String tenantId,
            @Param("roleId") String roleId);

    /**
     * 只把仍启用的行改成停用。返回影响行数。
     */
    int disableEnabled(
            @Param("toolId") String toolId,
            @Param("tenantId") String tenantId,
            @Param("roleId") String roleId);

    /**
     * 只把停用行改回启用。返回影响行数。
     */
    int restoreDisabled(
            @Param("toolId") String toolId,
            @Param("tenantId") String tenantId,
            @Param("roleId") String roleId);

    /**
     * 改优先级。不改启用状态。返回影响行数。
     */
    int updatePriority(
            @Param("toolId") String toolId,
            @Param("tenantId") String tenantId,
            @Param("roleId") String roleId,
            @Param("priority") int priority);
}
