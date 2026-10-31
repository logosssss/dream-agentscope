package com.zhu.semantic.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * tool_registry 的一行。只描述目录，不执行工具。
 */
@TableName("tool_registry")
public class ToolRegistry {

    /** 自增主键。目录匹配不看它。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具标识。和代码里的 toolId 精确对应。 */
    private String toolId;

    /** 展示名。 */
    private String name;

    /** 给模型看的说明。 */
    private String description;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 工具标识。 */
    public String getToolId() {
        return toolId;
    }

    /** 写入工具标识。 */
    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    /** 展示名。 */
    public String getName() {
        return name;
    }

    /** 写入展示名。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 说明。 */
    public String getDescription() {
        return description;
    }

    /** 写入说明。 */
    public void setDescription(String description) {
        this.description = description;
    }
}
