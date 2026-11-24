package com.zhu.docs.infrastructure;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文档内的标题锚点。按 GitHub 的方式把标题收成可重复使用的 id。
 * 一个实例服务一篇文档，跨文档不要复用，否则重复编号会串篇。
 */
public final class AnchorSlug {

    private final Set<String> used = new HashSet<>();

    /**
     * 标题转成锚点。空标题变成 {@code section}，避免出现空 id。
     * 同一篇里撞车时在后面加 {@code -1}、{@code -2}，已经占用的后缀会继续往后让。
     *
     * @param heading 不含 {@code ##} 的标题原文
     */
    public String next(String heading) {
        String base = base(heading);
        String candidate = base;
        int suffix = 0;
        while (!used.add(candidate)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    /**
     * 单次转换，不计重复。小写、去掉标点、连续空白收成一个连字符；汉字保留。
     * 连字符和下划线留着，否则 {@code agent-engineering} 会粘成一个词。
     *
     * @param heading 标题原文，可以是 null
     */
    public static String base(String heading) {
        if (heading == null || heading.isBlank()) {
            return "section";
        }
        String lower = heading.trim().toLowerCase(Locale.ROOT);
        StringBuilder slug = new StringBuilder();
        boolean gap = false;
        for (int i = 0; i < lower.length(); i++) {
            char current = lower.charAt(i);
            if (Character.isWhitespace(current)) {
                gap = slug.length() > 0;
                continue;
            }
            if (current == '_') {
                gap = false;
                slug.append('_');
                continue;
            }
            if (current == '-') {
                appendHyphen(slug);
                gap = false;
                continue;
            }
            if (isPunctuation(current) || !isSlugLetter(current)) {
                continue;
            }
            if (gap) {
                appendHyphen(slug);
                gap = false;
            }
            slug.append(current);
        }
        trimTrailingHyphen(slug);
        if (slug.length() == 0) {
            return "section";
        }
        return slug.toString();
    }

    /** 标点不进锚点。连字符已在前面单独处理，这里不再把各种横线当成正文。 */
    private static boolean isPunctuation(char current) {
        int type = Character.getType(current);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    /** 字母（含汉字）、数字和组合音标可以留下。符号和表情丢掉。 */
    private static boolean isSlugLetter(char current) {
        return Character.isLetterOrDigit(current) || isMark(current);
    }

    private static boolean isMark(char current) {
        int type = Character.getType(current);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static void appendHyphen(StringBuilder slug) {
        if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '-') {
            slug.append('-');
        }
    }

    private static void trimTrailingHyphen(StringBuilder slug) {
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
    }
}
