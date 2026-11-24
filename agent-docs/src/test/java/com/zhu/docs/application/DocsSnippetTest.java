package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 片段以块为中心，前后各 60 字，卡片不带整篇。 */
class DocsSnippetTest {

    @Test
    void keepsSixtyCharactersAroundTheChunk() {
        String chunk = "闸门顺序";
        String before = "甲".repeat(80);
        String after = "乙".repeat(80);
        String snippet = DocsSnippet.around(before + chunk + after, chunk);

        assertThat(snippet).startsWith("…");
        assertThat(snippet).endsWith("…");
        assertThat(snippet).contains(chunk);
        assertThat(snippet).doesNotContain("甲".repeat(61));
        assertThat(snippet).contains("甲".repeat(60));
        assertThat(snippet).contains("乙".repeat(60));
    }

    @Test
    void usesChunkTextWhenMarkdownDoesNotContainIt() {
        assertThat(DocsSnippet.around("别的正文", "## 标题\n正文")).isEqualTo("## 标题 正文");
    }
}
