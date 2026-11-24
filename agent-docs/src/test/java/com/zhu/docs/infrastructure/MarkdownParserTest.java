package com.zhu.docs.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 解析标题、front matter、二级节和相对链接。 */
class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();

    /** 一级标题、平铺 front matter、导语和两个二级节。 */
    @Test
    void readsTitleFrontMatterAndSections() {
        MarkdownDocument document = parser.parse("""
                ---
                author: ada
                title: "被一级标题覆盖"
                ---
                # 总标题

                导语

                ## 第一节
                甲

                ### 不是新节
                仍在第一节

                ## 第二节
                乙
                """);

        assertThat(document.title()).isEqualTo("总标题");
        assertThat(document.frontMatter()).containsEntry("author", "ada");
        assertThat(document.preamble()).isEqualTo("导语");
        assertThat(document.sections()).extracting(MarkdownSection::heading)
                .containsExactly("第一节", "第二节");
        assertThat(document.sections().get(0).body()).isEqualTo("甲\n\n### 不是新节\n仍在第一节");
        assertThat(document.sections().get(1).body()).isEqualTo("乙");
    }

    /** 没有一级标题时用 front matter 的 title。 */
    @Test
    void frontMatterTitleIsFallback() {
        MarkdownDocument document = parser.parse("""
                ---
                title: 只有这件事
                ---
                ## 正文
                内容
                """);

        assertThat(document.title()).isEqualTo("只有这件事");
    }

    /** 没闭合的 front matter 仍是正文，不能把后文吃掉。 */
    @Test
    void unclosedFrontMatterStaysInBody() {
        MarkdownDocument document = parser.parse("""
                ---
                title: 半截
                # 真标题
                ## 节
                内容
                """);

        assertThat(document.title()).isEqualTo("真标题");
        assertThat(document.sections()).hasSize(1);
    }

    /** 围栏里的二级标题和链接都不是结构。行内相对链接要留下，外链不要。 */
    @Test
    void skipsFencesAndKeepsRelativeLinks() {
        MarkdownDocument document = parser.parse("""
                # 标题

                见 [落地](./xxx-落地.md#锚点) 和 [外链](https://example.com/a)。

                ```md
                ## 这不是节
                [假链接](./nope.md)
                ```

                ## 真节
                """);

        assertThat(document.sections()).extracting(MarkdownSection::heading).containsExactly("真节");
        assertThat(document.links()).containsExactly(new MarkdownLink("落地", "./xxx-落地.md#锚点"));
    }
}
