package com.zhu.docs.application;

import com.zhu.docs.api.DocsSyncStatus;
import java.util.List;
import java.util.Optional;

/**
 * 文档索引的 MySQL 端口。实现可以整表丢掉再同步，不负责改 git。
 */
public interface DocsCatalog {

    /** 插入一轮 RUNNING 记录并返回 id。 */
    long openRun();

    /**
     * 结束一轮。note 超过列宽时由实现截断。
     */
    void closeRun(long id, int scanned, int added, int updated, int removed, String status, String note);

    /** 最近一轮。还没有时为空。 */
    Optional<DocsSyncStatus> latestRun();

    /** 按仓库和路径找，含已删除。没有时为空。 */
    Optional<CatalogDocument> findByRepoPath(String repo, String path);

    /**
     * 写入文档并换成这一批小节。旧小节删掉。新小节的 embeddedHash 由调用方决定。
     */
    void save(CatalogDocument document, List<CatalogChunk> chunks);

    /** 向量写成功后记下哈希。对不上 textHash 时下次还会再嵌。 */
    void markEmbedded(String chunkId, String textHash);

    /** 源文件没了。保留 id，不再出现在阅读接口里。 */
    void markDeleted(String id);

    /** 这个仓库里还没标删的文档，带小节哈希，不带正文。 */
    List<CatalogDocument> listActive(String repo);

    /**
     * 当前身份能看的文档，不带正文。owner 为 true 时包含私有文档。
     */
    List<CatalogDocument> listVisible(boolean owner);

    /**
     * 能看的一篇，带正文和小节。看不见时为空，不区分不存在和没权限。
     */
    Optional<CatalogDocument> findVisible(String id, boolean owner);

    /**
     * 同仓、当前身份能看见的路径和锚点。给阅读页替换相对链接，不带正文。
     * 私有文档对名单外不出现，避免把看不见的 id 写进公开页。
     */
    List<DocsLinkTarget> linkTargets(String repo, boolean owner);
}
