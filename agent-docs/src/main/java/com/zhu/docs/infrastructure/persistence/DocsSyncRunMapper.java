package com.zhu.docs.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * docs_sync_run 访问。只保留每次的计数，不存正文。
 */
@Mapper
public interface DocsSyncRunMapper extends BaseMapper<DocsSyncRunRow> {

    /** 最近一条。没有同步过时是 null。 */
    @Select("""
            SELECT id, started_at, finished_at, scanned, added, updated, removed, status, note
            FROM docs_sync_run
            ORDER BY id DESC
            LIMIT 1
            """)
    DocsSyncRunRow latest();
}
