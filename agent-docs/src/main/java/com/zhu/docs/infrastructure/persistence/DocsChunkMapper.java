package com.zhu.docs.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * docs_chunk 访问。换内容时整篇小节删掉重插，id 仍按序号稳定生成。
 */
@Mapper
public interface DocsChunkMapper extends BaseMapper<DocsChunkRow> {

    /** 一篇下面的全部小节，按出现顺序。 */
    @Select("""
            SELECT id, doc_id, heading, anchor, ordinal, text_hash, embedded_hash, `text`
            FROM docs_chunk
            WHERE doc_id = #{docId}
            ORDER BY ordinal
            """)
    List<DocsChunkRow> listByDoc(@Param("docId") String docId);

    /** 重写一篇之前先清掉旧小节。 */
    @Delete("DELETE FROM docs_chunk WHERE doc_id = #{docId}")
    int deleteByDoc(@Param("docId") String docId);

    /** 只在 Milvus 写入成功之后调用。哈希对不上就还会再嵌一次。 */
    @Update("UPDATE docs_chunk SET embedded_hash = #{hash} WHERE id = #{id}")
    int markEmbedded(@Param("id") String id, @Param("hash") String hash);

    /**
     * 按主键取当前身份能看的块。SQL 再滤一次可见性，不单信 Milvus 表达式。
     */
    @Select("""
            <script>
            SELECT c.id AS chunk_id, c.doc_id, c.heading, c.anchor, c.`text` AS chunk_text,
                   d.title, d.markdown
            FROM docs_chunk c
            JOIN docs_document d ON d.id = c.doc_id
            WHERE d.deleted = 0
              AND (d.visibility = 'public' OR (#{owner} = 1 AND d.visibility = 'private'))
              AND c.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<DocsSearchChunkRow> listByIds(@Param("ids") Collection<String> ids, @Param("owner") int owner);

    /**
     * 标题或块正文包含问句。pattern 已转义。空主题、空来源表示不限制。
     * 标题命中排在正文命中前面。
     */
    @Select("""
            SELECT c.id AS chunk_id, c.doc_id, c.heading, c.anchor, c.`text` AS chunk_text,
                   d.title, d.markdown
            FROM docs_chunk c
            JOIN docs_document d ON d.id = c.doc_id
            WHERE d.deleted = 0
              AND (d.visibility = 'public' OR (#{owner} = 1 AND d.visibility = 'private'))
              AND (#{topic} = '' OR d.topic = #{topic})
              AND (#{source} = '' OR d.source = #{source})
              AND (d.title LIKE #{pattern} ESCAPE '\\\\' OR c.`text` LIKE #{pattern} ESCAPE '\\\\')
            ORDER BY CASE WHEN d.title LIKE #{pattern} ESCAPE '\\\\' THEN 0 ELSE 1 END, d.indexed_at DESC
            LIMIT #{limit}
            """)
    List<DocsSearchChunkRow> like(
            @Param("pattern") String pattern,
            @Param("topic") String topic,
            @Param("source") String source,
            @Param("owner") int owner,
            @Param("limit") int limit);
}
