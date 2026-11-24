package com.zhu.docs.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * docs_document 访问。正文只在打开一篇时查出。
 */
@Mapper
public interface DocsDocumentMapper extends BaseMapper<DocsDocumentRow> {

    /** 含已删除行，同步要靠它认出同一个路径。 */
    @Select("""
            SELECT id, repo, path, title, topic, doc_type, visibility, source, content_hash,
                   indexed_at, deleted
            FROM docs_document
            WHERE repo = #{repo} AND path = #{path}
            """)
    DocsDocumentRow findByRepoPath(@Param("repo") String repo, @Param("path") String path);

    /** 还在源里的文档，不带正文。 */
    @Select("""
            SELECT id, repo, path, title, topic, doc_type, visibility, source, content_hash,
                   indexed_at, deleted
            FROM docs_document
            WHERE repo = #{repo} AND deleted = 0
            """)
    List<DocsDocumentRow> listActive(@Param("repo") String repo);

    /**
     * 当前身份能看的文档。owner 为 1 时带上私有文档。
     * 不把「存在但不可见」和「没有」分成两种查询。
     */
    @Select("""
            SELECT id, repo, path, title, topic, doc_type, visibility, source, content_hash,
                   indexed_at, deleted
            FROM docs_document
            WHERE deleted = 0
              AND (visibility = 'public' OR (#{owner} = 1 AND visibility = 'private'))
            """)
    List<DocsDocumentRow> listVisible(@Param("owner") int owner);

    /** 打开一篇。不可见的不返回。 */
    @Select("""
            SELECT id, repo, path, title, topic, doc_type, visibility, source, content_hash,
                   markdown, indexed_at, deleted
            FROM docs_document
            WHERE id = #{id} AND deleted = 0
              AND (visibility = 'public' OR (#{owner} = 1 AND visibility = 'private'))
            """)
    DocsDocumentRow findVisible(@Param("id") String id, @Param("owner") int owner);

    /**
     * 同仓可见文档的路径和锚点。没有小节的文档也会出现一行，anchor 为空。
     * 按路径和序号排，阅读页才能把重复标题的 -1 对上。
     */
    @Select("""
            SELECT d.id AS id, d.path AS path, c.anchor AS anchor
            FROM docs_document d
            LEFT JOIN docs_chunk c ON c.doc_id = d.id
            WHERE d.repo = #{repo} AND d.deleted = 0
              AND (d.visibility = 'public' OR (#{owner} = 1 AND d.visibility = 'private'))
            ORDER BY d.path, c.ordinal
            """)
    List<DocsLinkRow> listLinkTargets(@Param("repo") String repo, @Param("owner") int owner);
}
