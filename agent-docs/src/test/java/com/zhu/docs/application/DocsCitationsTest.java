package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.docs.api.DocsCitation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 引用只认提示词规定的链接写法。
 */
class DocsCitationsTest {

    /** 小节名和锚点分开取出，文档 id 用被问的那一篇。 */
    @Test
    void parsesSectionLinks() {
        List<DocsCitation> cites = DocsCitations.parse(
                "doc-1",
                "因为边界清楚。[五个窄端口](#五个窄端口) 所以要窄。");

        assertThat(cites).containsExactly(new DocsCitation("doc-1", "五个窄端口", "五个窄端口"));
    }

    /** 没有这种链接时不编造引用。 */
    @Test
    void absentAnswerHasNoCitations() {
        assertThat(DocsCitations.parse("doc-1", "本文未提及")).isEmpty();
    }

    /** 同一处重复出现只留一条，避免前端列出两次。 */
    @Test
    void duplicateLinkIsKeptOnce() {
        String answer = "[五个窄端口](#五个窄端口) 再看 [五个窄端口](#五个窄端口)";
        assertThat(DocsCitations.parse("doc-1", answer)).hasSize(1);
    }
}
