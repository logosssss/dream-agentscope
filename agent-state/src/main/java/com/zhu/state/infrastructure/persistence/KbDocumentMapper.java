package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * kb_document 访问。状态变更和按所有者查询在 XML。
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    KbDocument findOwned(
            @Param("docId") String docId,
            @Param("version") int version,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    Integer maxVersion(
            @Param("docId") String docId, @Param("tenantId") String tenantId, @Param("userId") String userId);

    int updateStatus(
            @Param("docId") String docId,
            @Param("version") int version,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("errorMsg") String errorMsg);

    /** 把同 doc 下 SUCCESS 改成 ARCHIVED。 */
    int archiveSuccess(
            @Param("docId") String docId, @Param("tenantId") String tenantId, @Param("userId") String userId);
}
