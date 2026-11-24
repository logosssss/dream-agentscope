package com.zhu.docs.application;

import com.zhu.docs.infrastructure.MarkdownDocument;
import com.zhu.docs.infrastructure.MarkdownParser;
import com.zhu.docs.infrastructure.MarkdownSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 送进模型的正文。整篇不超过 6000 个 Java 字符时原样使用。
 * 更长时只留文首段，再加上标题或正文命中问题的二级节，避免整篇塞进一次提问。
 */
public final class DocsExcerpt {

    /** 用户消息里的正文上限。按 Java 字符数，不是 token。 */
    public static final int LIMIT = 6000;

    private static final MarkdownParser PARSER = new MarkdownParser();

    private DocsExcerpt() {
    }

    /**
     * 按问题裁正文。没有命中的二级节时仍保留第一节，避免只剩一句导语。
     * 裁切发生在字符边界，不补省略号，以免模型把省略号当成原文。
     *
     * @param markdown 索引里的原文
     * @param question 用户问题
     */
    public static String fit(String markdown, String question) {
        String body = markdown == null ? "" : markdown;
        if (body.length() <= LIMIT) {
            return body;
        }
        MarkdownDocument document = PARSER.parse(body);
        List<String> pieces = new ArrayList<>();
        String intro = document.preamble() == null ? "" : document.preamble().strip();
        if (!intro.isEmpty()) {
            pieces.add(intro);
        }
        List<MarkdownSection> hits = matching(document.sections(), question);
        if (hits.isEmpty() && !document.sections().isEmpty()) {
            hits = List.of(document.sections().get(0));
        }
        for (MarkdownSection section : hits) {
            pieces.add(render(section));
        }
        return joinWithin(pieces, LIMIT);
    }

    /**
     * 拼进用户消息。系统提示词不在这里，避免和 Agent 上的原文各写一份。
     *
     * @param title    文档标题
     * @param body     已经截断的正文
     * @param question 用户问题，调用方保证非空
     */
    public static String userMessage(String title, String body, String question) {
        String name = title == null || title.isBlank() ? "未命名" : title.strip();
        String text = body == null ? "" : body;
        return "文档标题：" + name + "\n\n文档正文：\n" + text + "\n\n问题：" + question;
    }

    /**
     * 标题被问题包含、问题被标题或正文包含，都算命中。
     * 中文问题往往没有空格，所以整句也要拿去对标题。
     */
    static List<MarkdownSection> matching(List<MarkdownSection> sections, String question) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        String asked = question == null ? "" : question.strip().toLowerCase(Locale.ROOT);
        if (asked.length() < 2) {
            return List.of();
        }
        List<String> tokens = tokens(asked);
        List<MarkdownSection> hits = new ArrayList<>();
        for (MarkdownSection section : sections) {
            String heading = lower(section.heading());
            String text = lower(section.body());
            // 两三个字的标题太容易撞进问句，例如「部署」。四字及以上才把标题当成目录命中。
            if (heading.length() >= 4 && asked.contains(heading)) {
                hits.add(section);
                continue;
            }
            if (contains(heading, asked) || contains(text, asked) || tokenHit(heading, text, tokens)) {
                hits.add(section);
            }
        }
        return hits;
    }

    /** 按标点和空白切开。整句本身不再当作一个 token，避免和上面的整句判断重复。 */
    private static List<String> tokens(String question) {
        String[] parts = question.split("[^\\p{IsHan}\\p{L}\\p{N}_-]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2 && !part.equals(question)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /** 问句里拆出来的词出现在标题或正文里，也算这一节被问到。 */
    private static boolean tokenHit(String heading, String text, List<String> tokens) {
        for (String token : tokens) {
            if (contains(heading, token) || contains(text, token)) {
                return true;
            }
        }
        return false;
    }

    /** 太短的词不匹配，避免单字标题把整节都带上。 */
    private static boolean contains(String text, String needle) {
        return needle.length() >= 2 && text.contains(needle);
    }

    /** 比较前转小写。空标题当成空串，避免空指针。 */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /** 还原成带二级标题的一段，方便模型按小节引用。 */
    private static String render(MarkdownSection section) {
        String heading = section.heading() == null ? "" : section.heading().strip();
        String text = section.body() == null ? "" : section.body().strip();
        if (text.isEmpty()) {
            return "## " + heading;
        }
        return "## " + heading + "\n\n" + text;
    }

    /** 按顺序往里填，装不下的节只留下还放得下的开头。 */
    private static String joinWithin(List<String> pieces, int limit) {
        StringBuilder out = new StringBuilder();
        for (String piece : pieces) {
            if (piece == null || piece.isBlank()) {
                continue;
            }
            if (out.length() >= limit) {
                break;
            }
            String text = piece.strip();
            int gap = out.isEmpty() ? 0 : 2;
            int room = limit - out.length() - gap;
            if (room <= 0) {
                break;
            }
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append(text.length() <= room ? text : text.substring(0, room));
        }
        return out.toString();
    }
}
