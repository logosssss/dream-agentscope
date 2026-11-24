package com.zhu.docs.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 一个二级标题一份块，标题写进待嵌入的文本。 */
class ChunkerTest {

    private final Chunker chunker = new Chunker();

    /** 导语不单独成块。三级标题留在上一块正文里。 */
    @Test
    void oneSectionBecomesOneChunk() {
        ListChunks chunks = chunks("""
                # 标题

                导语

                ## 第一节
                甲

                ### 细节
                乙

                ## 第二节
                """);

        assertThat(chunks.list()).hasSize(2);
        assertThat(chunks.list().get(0).ordinal()).isZero();
        assertThat(chunks.list().get(0).heading()).isEqualTo("第一节");
        assertThat(chunks.list().get(0).anchor()).isEqualTo("第一节");
        assertThat(chunks.list().get(0).text()).isEqualTo("## 第一节\n\n甲\n\n### 细节\n乙");
        assertThat(chunks.list().get(1).text()).isEqualTo("## 第二节");
        assertThat(chunks.list().get(1).ordinal()).isEqualTo(1);
    }

    /** 重复标题的锚点加 -1。带反引号的标题先去标点再编号。 */
    @Test
    void duplicateAndBacktickHeadings() {
        ListChunks chunks = chunks("""
                ## 使用 `Code` 的标题
                甲

                ## 使用 `Code` 的标题
                乙
                """);

        assertThat(chunks.list()).extracting(DocChunk::anchor)
                .containsExactly("使用-code-的标题", "使用-code-的标题-1");
        assertThat(chunks.list().get(0).text()).startsWith("## 使用 `Code` 的标题\n\n甲");
    }

    /** 没有二级标题时不产生块。 */
    @Test
    void noSectionYieldsNothing() {
        assertThat(chunker.chunk("# 只有标题\n\n正文")).isEmpty();
    }

    private ListChunks chunks(String markdown) {
        return new ListChunks(chunker.chunk(markdown));
    }

    /** 让断言读起来短一点。测试夹具，不是产品类型。 */
    private record ListChunks(java.util.List<DocChunk> list) {
    }
}
