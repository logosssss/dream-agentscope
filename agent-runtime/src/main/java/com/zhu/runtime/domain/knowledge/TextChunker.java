package com.zhu.runtime.domain.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度切块。阶段一 Naive，不做语义切块。
 */
public final class TextChunker {

    /** 默认块长（字符）。 */
    public static final int DEFAULT_SIZE = 500;

    /** 默认重叠（字符）。 */
    public static final int DEFAULT_OVERLAP = 50;

    private TextChunker() {
    }

    /**
     * 按字符切分。空白返回空列表。size 小于 1 时按默认；overlap 会压到小于 size。
     */
    public static List<String> chunk(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int block = size < 1 ? DEFAULT_SIZE : size;
        int ov = Math.max(0, Math.min(overlap, block - 1));
        String raw = text;
        List<String> parts = new ArrayList<>();
        int start = 0;
        int length = raw.length();
        while (start < length) {
            int end = Math.min(length, start + block);
            String piece = raw.substring(start, end).trim();
            if (!piece.isEmpty()) {
                parts.add(piece);
            }
            if (end >= length) {
                break;
            }
            start = end - ov;
            if (start <= 0) {
                start = end;
            }
        }
        return parts;
    }
}
