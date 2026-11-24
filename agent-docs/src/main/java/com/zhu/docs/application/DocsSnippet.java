package com.zhu.docs.application;

/**
 * 从整篇里切出命中块前后的阅读片段。搜索卡片只展示这一小段。
 */
public final class DocsSnippet {

    /** 块前面、后面各留这么多字。先不按分数截断，字数是固定的。 */
    public static final int RADIUS = 60;

    private DocsSnippet() {
    }

    /**
     * 在正文里找到块，向前向后各取 {@link #RADIUS} 字。
     * 块不在正文里时只返回块文本，避免卡片空白。
     *
     * @param markdown 整篇，可以是 null
     * @param chunk    块文本，含 {@code ##} 标题
     */
    public static String around(String markdown, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return "";
        }
        String body = markdown == null ? "" : markdown;
        int at = body.indexOf(chunk);
        if (at < 0) {
            return squash(chunk);
        }
        int start = Math.max(0, at - RADIUS);
        int end = Math.min(body.length(), at + chunk.length() + RADIUS);
        String slice = squash(body.substring(start, end));
        if (start > 0) {
            slice = "…" + slice;
        }
        if (end < body.length()) {
            slice = slice + "…";
        }
        return slice;
    }

    /** 卡片是一行一段，换行收成空格，避免把目录撑开。 */
    private static String squash(String text) {
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
