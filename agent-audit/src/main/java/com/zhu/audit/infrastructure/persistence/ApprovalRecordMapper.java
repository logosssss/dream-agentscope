package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * approval_record 的 Mapper。简单插入用 BaseMapper，待审批查询在同名 XML 里。
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    /**
     * 只取 PENDING，按主键从新到旧。条数由调用方收口后再传入。
     */
    List<ApprovalRecord> pending(@Param("limit") int limit);
}
