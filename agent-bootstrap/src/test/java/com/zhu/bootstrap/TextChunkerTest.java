package com.zhu.bootstrap;

import com.zhu.runtime.domain.knowledge.TextChunker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定切块。不连模型。
 */
class TextChunkerTest {

    @Test
    void splitsWithOverlap() {
        String text = "a".repeat(120);
        assertThat(TextChunker.chunk(text, 50, 10)).hasSize(3);
        assertThat(TextChunker.chunk("  ", 50, 10)).isEmpty();
        assertThat(TextChunker.chunk("short", 500, 50)).containsExactly("short");
    }
}
