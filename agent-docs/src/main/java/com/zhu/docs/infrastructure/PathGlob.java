package com.zhu.docs.infrastructure;

/**
 * 用 {@code /} 分隔的 glob。{@code *} 不跨目录，{@code **} 可以跨，也可以匹配空路径。
 * 给文档白名单用，不处理花括号和字符类。
 */
final class PathGlob {

    /** 只有静态匹配，不保留状态。 */
    private PathGlob() {
    }

    /**
     * 相对路径是否命中模式。路径和模式都用 {@code /}，不要带盘符。
     * 空模式不命中任何路径。
     *
     * @param pattern      include 或 exclude 里的一条
     * @param relativePath 相对仓库根的路径
     */
    static boolean matches(String pattern, String relativePath) {
        if (pattern == null || pattern.isBlank() || relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String[] parts = trimSlashes(pattern).split("/");
        String[] path = trimSlashes(relativePath).split("/");
        return match(parts, 0, path, 0);
    }

    private static String trimSlashes(String value) {
        String trimmed = value.replace('\\', '/');
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * {@code **} 从当前段开始尽量少吃，让后面的模式还能对上。
     * 模式走完时路径也必须走完，避免 {@code README.md} 命中子目录里的同名文件。
     */
    private static boolean match(String[] pattern, int patternIndex, String[] path, int pathIndex) {
        if (patternIndex == pattern.length) {
            return pathIndex == path.length;
        }
        if ("**".equals(pattern[patternIndex])) {
            if (patternIndex == pattern.length - 1) {
                return true;
            }
            for (int skip = pathIndex; skip <= path.length; skip++) {
                if (match(pattern, patternIndex + 1, path, skip)) {
                    return true;
                }
            }
            return false;
        }
        if (pathIndex >= path.length || !partMatches(pattern[patternIndex], path[pathIndex])) {
            return false;
        }
        return match(pattern, patternIndex + 1, path, pathIndex + 1);
    }

    /** 一段里的 {@code *} 和 {@code ?}。没有通配符时整段相等。 */
    private static boolean partMatches(String pattern, String segment) {
        return partMatch(pattern, 0, segment, 0);
    }

    /** {@code *} 从当前字符起尝试每种长度，让后面的字面量还能对上。 */
    private static boolean partMatch(String pattern, int patternIndex, String segment, int segmentIndex) {
        if (patternIndex == pattern.length()) {
            return segmentIndex == segment.length();
        }
        char current = pattern.charAt(patternIndex);
        if (current == '*') {
            for (int skip = segmentIndex; skip <= segment.length(); skip++) {
                if (partMatch(pattern, patternIndex + 1, segment, skip)) {
                    return true;
                }
            }
            return false;
        }
        if (segmentIndex >= segment.length()) {
            return false;
        }
        if (current == '?' || current == segment.charAt(segmentIndex)) {
            return partMatch(pattern, patternIndex + 1, segment, segmentIndex + 1);
        }
        return false;
    }
}
