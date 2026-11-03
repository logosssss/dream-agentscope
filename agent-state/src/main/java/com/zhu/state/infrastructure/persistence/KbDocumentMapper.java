package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.KbDocument;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * kb_document 访问。状态变更和按所有者查询在 XML。
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /** 版本号最大的一行，不含 content。 */
    KbDocument findLatestOwned(
            @Param("docId") String docId, @Param("tenantId") String tenantId, @Param("userId") String userId);

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

    /** 某库下每个文档最新一版，不含 content。 */
    List<KbDocument> listLatestByKb(
            @Param("kbId") String kbId, @Param("tenantId") String tenantId, @Param("userId") String userId);

    int deleteByDoc(
            @Param("docId") String docId, @Param("tenantId") String tenantId, @Param("userId") String userId);

    int deleteByKb(
            @Param("kbId") String kbId, @Param("tenantId") String tenantId, @Param("userId") String userId);
}
