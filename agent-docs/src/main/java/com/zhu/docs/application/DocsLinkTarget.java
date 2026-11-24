package com.zhu.docs.application;

import java.util.List;

/**
 * 当前身份在同一个仓库里能打开的一篇。只带路径和锚点，不带正文。
 *
 * @param id      稳定文档 id
 * @param path    相对仓库根，分隔符是 {@code /}
 * @param anchors 这篇二级标题的锚点，按出现顺序
 */
public record DocsLinkTarget(String id, String path, List<String> anchors) {
}
