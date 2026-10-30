package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * approval_record 的 Mapper。简单插入用 BaseMapper，字段映射在同名 XML 里。
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {
}
