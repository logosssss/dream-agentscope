package com.zhu.docs.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 锚点：汉字留下，标点去掉，重复标题加后缀。 */
class AnchorTest {

    /** 中文标题没有空白，锚点就是标题本身。 */
    @Test
    void keepsChinese() {
        assertThat(AnchorSlug.base("中文标题")).isEqualTo("中文标题");
    }

    /** 反引号是标点，两侧空白收成一个连字符。 */
    @Test
    void dropsBackticks() {
        assertThat(AnchorSlug.base("使用 `code` 的标题")).isEqualTo("使用-code-的标题");
    }

    /** 英文小写，标点去掉，空白变成连字符。 */
    @Test
    void foldsEnglishPunctuation() {
        assertThat(AnchorSlug.base("Hello, World!")).isEqualTo("hello-world");
    }

    /** 同一篇里第二个同样的标题用 -1，第三个用 -2。 */
    @Test
    void suffixesDuplicates() {
        AnchorSlug anchors = new AnchorSlug();
        assertThat(anchors.next("安装")).isEqualTo("安装");
        assertThat(anchors.next("安装")).isEqualTo("安装-1");
        assertThat(anchors.next("安装")).isEqualTo("安装-2");
    }

    /** 去掉标点后什么都不剩时用 section，避免空锚点。 */
    @Test
    void blankBecomesSection() {
        assertThat(AnchorSlug.base("!!!")).isEqualTo("section");
    }
}
