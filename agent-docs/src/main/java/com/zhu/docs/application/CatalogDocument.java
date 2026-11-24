package com.zhu.docs.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库里的一篇文档。chunks 在列表查询里可以是空的，打开详情或同步时再带上。
 *
 * @param id          稳定 id
 * @param repo        仓库键
 * @param path        相对路径
 * @param title       标题
 * @param topic       主题
 * @param docType     路径种类
 * @param visibility  public 或 private
 * @param source      与 repo 相同
 * @param contentHash 整篇哈希
 * @param markdown    整篇正文，列表查询时为 null
 * @param indexedAt   写入时间
 * @param deleted     源文件已消失。id 保留，阅读接口不再返回
 * @param chunks      小节，列表查询时为空
 */
public record CatalogDocument(
        String id,
        String repo,
        String path,
        String title,
        String topic,
        String docType,
        String visibility,
        String source,
        String contentHash,
        String markdown,
        LocalDateTime indexedAt,
        boolean deleted,
        List<CatalogChunk> chunks) {
}
