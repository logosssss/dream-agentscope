package com.zhu.docs.api;

/**
 * 答案里引用的一节。锚点来自模型按提示词写出的 {@code [小节名](#anchor)}。
 *
 * @param docId  被问的那一篇
 * @param anchor 小节锚点，不含 {@code #}
 * @param quote  链接文字，也就是小节名
 */
public record DocsCitation(String docId, String anchor, String quote) {
}
