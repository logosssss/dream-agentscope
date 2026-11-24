package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 超长文档只留文首和命中的节。不调用模型。
 */
class DocsExcerptTest {

    /** 短文原样送出，避免无谓裁切改掉引用锚点。 */
    @Test
    void shortMarkdownStaysWhole() {
        String markdown = """
                # 标题

                导语。

                ## 五个窄端口

                端口要窄。
                """;
        assertThat(DocsExcerpt.fit(markdown, "为什么要五个窄端口")).isEqualTo(markdown);
    }

    /** 长文命中标题时带上那一节，没问到的后文不带。 */
    @Test
    void longMarkdownKeepsIntroAndMatchingSection() {
        String markdown = "# 标题\n\n导语。\n\n## 五个窄端口\n\n端口要窄，因为边界要清楚。\n\n## 别的主题\n\n"
                + "无关尾部。".repeat(1200);
        String excerpt = DocsExcerpt.fit(markdown, "为什么要五个窄端口");

        assertThat(markdown.length()).isGreaterThan(DocsExcerpt.LIMIT);
        assertThat(excerpt).contains("导语。");
        assertThat(excerpt).contains("五个窄端口");
        assertThat(excerpt).contains("端口要窄");
        assertThat(excerpt).doesNotContain("无关尾部");
        assertThat(excerpt.length()).isLessThanOrEqualTo(DocsExcerpt.LIMIT);
    }

    /** 问题对不上任何标题时，只留文首和第一节，不把后文塞进去。 */
    @Test
    void unmatchedQuestionKeepsFirstSectionOnly() {
        String markdown = "# 标题\n\n导语。\n\n## 五个窄端口\n\n端口要窄。\n\n## 别的主题\n\n"
                + "无关尾部。".repeat(1200);
        String excerpt = DocsExcerpt.fit(markdown, "这篇里 Kubernetes 怎么部署");

        assertThat(markdown.length()).isGreaterThan(DocsExcerpt.LIMIT);
        assertThat(excerpt).contains("导语。");
        assertThat(excerpt).contains("五个窄端口");
        assertThat(excerpt).doesNotContain("无关尾部");
        assertThat(excerpt.length()).isLessThanOrEqualTo(DocsExcerpt.LIMIT);
    }

    /** 用户消息带标题、正文和问题，系统提示不混进来。 */
    @Test
    void userMessageCarriesTheQuestion() {
        String message = DocsExcerpt.userMessage("测试", "正文", "为什么要五个窄端口");
        assertThat(message).contains("文档标题：测试");
        assertThat(message).contains("文档正文：\n正文");
        assertThat(message).contains("问题：为什么要五个窄端口");
        assertThat(message).doesNotContain("你是文档助手");
    }
}
