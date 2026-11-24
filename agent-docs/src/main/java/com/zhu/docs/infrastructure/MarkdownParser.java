package com.zhu.docs.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 解析器。抽出标题、文首 front matter、二级节和行内相对链接。
 * 不访问磁盘，也不做跨文件的文档 id 替换。
 */
public final class MarkdownParser {

    private static final Pattern H1 = Pattern.compile("^#\\s+(.+?)(?:\\s+#*)?\\s*$");

    private static final Pattern H2 = Pattern.compile("^##\\s+(.+?)(?:\\s+#*)?\\s*$");

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\(([^)]*)\\)");

    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");

    /**
     * 解析整篇文本。null 当成空文档，避免一篇坏文件中断扫描。
     * 围栏代码块里的标题和链接不当成结构。
     *
     * @param markdown 原始 Markdown，可以是 null
     */
    public MarkdownDocument parse(String markdown) {
        String source = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        String[] lines = source.split("\n", -1);
        int bodyStart = 0;
        Map<String, String> frontMatter = Map.of();
        if (lines.length > 0 && "---".equals(lines[0])) {
            int end = closingFence(lines);
            if (end > 0) {
                frontMatter = frontMatter(lines, end);
                bodyStart = end + 1;
            }
        }
        List<String> bodyLines = List.of(lines).subList(bodyStart, lines.length);
        ParsedBody body = readBody(bodyLines);
        String title = body.title;
        if (title.isBlank()) {
            title = frontMatter.getOrDefault("title", "");
        }
        return new MarkdownDocument(title, frontMatter, body.preamble, body.sections, body.links);
    }

    /**
     * 找到与文首 {@code ---} 配对的结束行。没闭合就不当成 front matter，原文仍留给正文。
     */
    private static int closingFence(String[] lines) {
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 只收顶格的 {@code key: value}。缩进行和嵌套块跳过，这里不实现一套 YAML。
     */
    private static Map<String, String> frontMatter(String[] lines, int end) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < end; i++) {
            String line = lines[i];
            if (line.isBlank() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = unquote(line.substring(colon + 1).trim());
            if (!key.isEmpty()) {
                values.put(key, value);
            }
        }
        return Map.copyOf(values);
    }

    /** 去掉成对的单引号或双引号。不成对就原样留下。 */
    private static String unquote(String value) {
        if (value.length() >= 2) {
            char open = value.charAt(0);
            char close = value.charAt(value.length() - 1);
            if ((open == '"' && close == '"') || (open == '\'' && close == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * 扫描正文。一级标题只取第一个；二级标题切开；更深的标题留在当前节里。
     */
    private static ParsedBody readBody(List<String> lines) {
        String title = "";
        List<MarkdownLink> links = new ArrayList<>();
        List<MarkdownSection> sections = new ArrayList<>();
        StringBuilder preamble = new StringBuilder();
        StringBuilder current = null;
        String currentHeading = null;
        boolean fenced = false;
        String fenceMark = "";
        for (String line : lines) {
            String opened = fenceMark(line);
            if (opened != null) {
                if (!fenced) {
                    fenced = true;
                    fenceMark = opened;
                } else if (opened.equals(fenceMark)) {
                    fenced = false;
                    fenceMark = "";
                }
                appendLine(current, preamble, line);
                continue;
            }
            if (fenced) {
                appendLine(current, preamble, line);
                continue;
            }
            links.addAll(linksIn(stripInlineCode(line)));
            Matcher h2 = H2.matcher(line);
            if (h2.matches()) {
                flush(sections, currentHeading, current);
                currentHeading = h2.group(1).trim();
                current = new StringBuilder();
                continue;
            }
            if (title.isEmpty()) {
                Matcher h1 = H1.matcher(line);
                if (h1.matches() && !line.startsWith("##")) {
                    title = h1.group(1).trim();
                    continue;
                }
            }
            appendLine(current, preamble, line);
        }
        flush(sections, currentHeading, current);
        return new ParsedBody(title, trimBlock(preamble), List.copyOf(sections), List.copyOf(links));
    }

    /**
     * 围栏标记。行首最多三个空格后是连续的 {@code ```} 或 {@code ~~~} 才算。
     * 返回标记本身，普通行返回 null。
     */
    private static String fenceMark(String line) {
        int index = 0;
        while (index < line.length() && index < 3 && line.charAt(index) == ' ') {
            index++;
        }
        if (index >= line.length()) {
            return null;
        }
        char mark = line.charAt(index);
        if (mark != '`' && mark != '~') {
            return null;
        }
        int count = 0;
        while (index + count < line.length() && line.charAt(index + count) == mark) {
            count++;
        }
        if (count < 3) {
            return null;
        }
        return String.valueOf(mark);
    }

    /** 行内代码不参与链接识别，避免把示例里的地址收成链接。 */
    private static String stripInlineCode(String line) {
        return INLINE_CODE.matcher(line).replaceAll("");
    }

    /**
     * 抽出这一行的相对链接。图片、外链、站内绝对路径和纯锚点不要。
     * 参考式 {@code [text][id]} 不在这里解，同步阶段还没有定义表。
     */
    private static List<MarkdownLink> linksIn(String line) {
        List<MarkdownLink> found = new ArrayList<>();
        Matcher matcher = LINK.matcher(line);
        while (matcher.find()) {
            int start = matcher.start();
            if (start > 0 && line.charAt(start - 1) == '!') {
                continue;
            }
            String target = destination(matcher.group(2));
            if (target == null || !isRelative(target)) {
                continue;
            }
            found.add(new MarkdownLink(matcher.group(1), target));
        }
        return found;
    }

    /** 取括号里的地址。尖括号形式去掉括号，后面的标题丢掉。空地址返回 null。 */
    private static String destination(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.charAt(0) == '<') {
            int close = value.indexOf('>');
            if (close > 1) {
                return value.substring(1, close).trim();
            }
        }
        int space = value.indexOf(' ');
        if (space >= 0) {
            value = value.substring(0, space);
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 同仓相对路径才留下。带协议、纯锚点、以 {@code /} 开头的都不算文件间链接。
     */
    private static boolean isRelative(String target) {
        if (target.startsWith("#") || target.startsWith("/")) {
            return false;
        }
        return !target.contains("://") && !target.startsWith("mailto:");
    }

    private static void appendLine(StringBuilder current, StringBuilder preamble, String line) {
        StringBuilder target = current == null ? preamble : current;
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(line);
    }

    private static void flush(List<MarkdownSection> sections, String heading, StringBuilder body) {
        if (heading == null) {
            return;
        }
        sections.add(new MarkdownSection(heading, trimBlock(body)));
    }

    private static String trimBlock(StringBuilder block) {
        if (block == null) {
            return "";
        }
        return block.toString().trim();
    }

    /** 正文扫描的中间结果。front matter 已经拆走。 */
    private record ParsedBody(
            String title, String preamble, List<MarkdownSection> sections, List<MarkdownLink> links) {
    }
}
