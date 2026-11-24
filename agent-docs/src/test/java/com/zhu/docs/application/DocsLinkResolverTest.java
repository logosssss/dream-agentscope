package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.docs.api.DocsResolvedLink;
import com.zhu.docs.infrastructure.MarkdownParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 相对链接只在目标对当前身份可见时换成文档地址。
 * 这里不连库，目标表由调用方给。
 */
class DocsLinkResolverTest {

    private final DocsLinkResolver resolver = new DocsLinkResolver(new MarkdownParser());

    /** 同目录的落地文要进详情路由，不能落到主题路由上。 */
    @Test
    void relativeMarkdownBecomesDocRoute() {
        List<DocsLinkTarget> targets = List.of(
                new DocsLinkTarget("doc-landing", "docs/handbook/xxx-落地.md", List.of("做法")),
                new DocsLinkTarget("doc-self", "docs/handbook/a.md", List.of()));

        assertThat(resolver.resolve(
                        "docs/handbook/a.md",
                        "见 [落地](./xxx-落地.md) 和 [一节](./xxx-落地.md#做法)。",
                        targets))
                .containsExactly(
                        new DocsResolvedLink("./xxx-落地.md", "/docs/doc/doc-landing"),
                        new DocsResolvedLink("./xxx-落地.md#做法", "/docs/doc/doc-landing#%E5%81%9A%E6%B3%95"));
    }

    /** 外链、图片、跳出仓库根、以及当前身份看不见的路径都保持原样。 */
    @Test
    void skipsOutsideAndHidden() {
        List<DocsLinkTarget> targets = List.of(
                new DocsLinkTarget("public-id", "docs/ok.md", List.of("second-title")));

        assertThat(resolver.resolve(
                        "docs/sub/a.md",
                        """
                        [外](https://example.com/a.md)
                        [图](./a.png)
                        [出](../../../secret.md)
                        [藏](./hidden.md)
                        [节](../ok.md#Second-Title)
                        """,
                        targets))
                .containsExactly(new DocsResolvedLink(
                        "../ok.md#Second-Title", "/docs/doc/public-id#second-title"));
    }

    /** 重复标题已经编成 -1 时，片段对上已有锚点，不再重新从 0 编号。 */
    @Test
    void keepsDuplicateAnchor() {
        List<DocsLinkTarget> targets = List.of(
                new DocsLinkTarget("doc-1", "docs/a.md", List.of("标题", "标题-1")));

        assertThat(resolver.resolve("docs/b.md", "[二](./a.md#标题-1)", targets))
                .containsExactly(new DocsResolvedLink(
                        "./a.md#标题-1", "/docs/doc/doc-1#%E6%A0%87%E9%A2%98-1"));
    }
}
