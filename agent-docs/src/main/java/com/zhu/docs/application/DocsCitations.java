package com.zhu.docs.application;

import com.zhu.docs.api.DocsCitation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从答案里抽出提示词要求的小节链接。模型不返回 JSON，所以只能认这一种写法。
 */
public final class DocsCitations {

    /** {@code [小节名](#anchor)}。换行和空格不算进锚点，避免把下一句吞进去。 */
    private static final Pattern CITE = Pattern.compile("\\[([^\\]\\n]+)]\\(#([^)\\s]+)\\)");

    private DocsCitations() {
    }

    /**
     * 按出现顺序收集。同一锚点加同一小节名只留第一次。
     * 没有这种链接时返回空列表，例如答案是「本文未提及」。
     *
     * @param docId  被问的文档
     * @param answer 模型原文
     */
    public static List<DocsCitation> parse(String docId, String answer) {
        if (answer == null || answer.isEmpty()) {
            return List.of();
        }
        Matcher matcher = CITE.matcher(answer);
        List<DocsCitation> cites = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            String quote = matcher.group(1).strip();
            String anchor = matcher.group(2).strip();
            if (quote.isEmpty() || anchor.isEmpty() || !seen.add(anchor + "\0" + quote)) {
                continue;
            }
            cites.add(new DocsCitation(docId, anchor, quote));
        }
        return List.copyOf(cites);
    }
}
