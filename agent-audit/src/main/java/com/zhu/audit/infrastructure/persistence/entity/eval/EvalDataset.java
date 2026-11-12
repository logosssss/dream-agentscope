package com.zhu.audit.infrastructure.persistence.entity.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * eval_dataset 的一行。只描述数据集，不包含用例。
 */
@TableName("eval_dataset")
public class EvalDataset {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据集标识。 */
    private String datasetId;

    /** 名称。 */
    private String name;

    /** 固定 1.0.0。 */
    private String version;

    /** 短说明。没有时留空。 */
    private String description;

    /** 固定为 1。这次不停用。 */
    private Integer enabled;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 数据集标识。 */
    public String getDatasetId() {
        return datasetId;
    }

    /** 写入数据集标识。 */
    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    /** 名称。 */
    public String getName() {
        return name;
    }

    /** 写入名称。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 版本。 */
    public String getVersion() {
        return version;
    }

    /** 写入版本。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 说明。 */
    public String getDescription() {
        return description;
    }

    /** 写入说明。 */
    public void setDescription(String description) {
        this.description = description;
    }

    /** 是否启用。 */
    public Integer getEnabled() {
        return enabled;
    }

    /** 写入启用标记。 */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
