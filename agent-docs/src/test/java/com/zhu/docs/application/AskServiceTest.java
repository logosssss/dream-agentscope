package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.docs.api.DocsAskPrompt;
import com.zhu.docs.api.DocsAskQuery;
import com.zhu.docs.api.DocsAskResult;
import com.zhu.docs.api.DocsPage;
import com.zhu.docs.api.DocsQuery;
import com.zhu.docs.api.DocsTree;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * 提问编排。模型用假的补全，不连 Redis 或供应商。
 */
class AskServiceTest {

    /** 系统提示必须是原文，正文和问题都在用户消息里。 */
    @Test
    void sendsVerbatimPromptAndParsesCitation() {
        FakeQuery query = new FakeQuery(page("五个窄端口要保持边界。"));
        FakeCompleter completer = new FakeCompleter("见 [五个窄端口](#五个窄端口)。");
        AskService service = new AskService(query, completer, new OpenGate());

        DocsAskResult result = service.ask("user-1", "doc-1", new DocsAskQuery("为什么要五个窄端口"));

        assertThat(completer.systemPrompt).isEqualTo(DocsAskPrompt.SYSTEM);
        assertThat(completer.userText).contains("五个窄端口要保持边界。");
        assertThat(completer.userText).contains("问题：为什么要五个窄端口");
        assertThat(result.answer()).contains("五个窄端口");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).anchor()).isEqualTo("五个窄端口");
    }

    /** 未登录不能把正文送进模型。 */
    @Test
    void anonymousIsRejectedBeforeTheModel() {
        FakeCompleter completer = new FakeCompleter("不应出现");
        AskService service = new AskService(new FakeQuery(page("秘密")), completer, new OpenGate());

        assertThatThrownBy(() -> service.ask(" ", "doc-1", new DocsAskQuery("问题")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(completer.userText).isNull();
    }

    /** 看不见的文档在扣额度和调用模型之前就结束。 */
    @Test
    void hiddenDocumentDoesNotCallTheModel() {
        FakeQuery query = new FakeQuery(null);
        query.error = new BusinessException(ErrorCode.NOT_FOUND, "not found");
        FakeCompleter completer = new FakeCompleter("不应出现");
        OpenGate gate = new OpenGate();
        AskService service = new AskService(query, completer, gate);

        assertThatThrownBy(() -> service.ask("user-1", "missing", new DocsAskQuery("问题")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(completer.userText).isNull();
        assertThat(gate.entered).isFalse();
    }

    /** 固定提示词的四条规则都在，避免以后改写时测不出来。 */
    @Test
    void systemPromptIsTheAgreedText() {
        assertThat(DocsAskPrompt.SYSTEM).isEqualTo("""
                你是文档助手，只能依据用户提供的这一篇文档回答。

                规则：
                1. 答案必须来自本文；引用时给出小节锚点，格式 [小节名](#anchor)。
                2. 本文没有的内容，直接回答「本文未提及」，禁止用你的通用知识补充。
                3. 不要复述整篇文档，只回答被问到的部分，控制在 200 字内。
                4. 如果问题模糊，先猜最可能的意图回答，再补一句「如果你问的是 X，请说明」。
                """.strip());
    }

    private static DocsPage page(String markdown) {
        return new DocsPage("测试文档", "agent-engineering", markdown, List.of(), LocalDateTime.now(), List.of());
    }

    private static final class FakeQuery implements DocsQuery {
        private final DocsPage page;
        private BusinessException error;

        private FakeQuery(DocsPage page) {
            this.page = page;
        }

        @Override
        public DocsTree tree(String userId) {
            return null;
        }

        @Override
        public DocsPage page(String userId, String id) {
            if (error != null) {
                throw error;
            }
            return page;
        }
    }

    private static final class FakeCompleter implements com.zhu.docs.api.DocsCompleter {
        private final String answer;
        private String systemPrompt;
        private String userText;

        private FakeCompleter(String answer) {
            this.answer = answer;
        }

        @Override
        public String complete(String systemPrompt, String userText) {
            this.systemPrompt = systemPrompt;
            this.userText = userText;
            return answer;
        }
    }

    private static final class OpenGate implements com.zhu.docs.api.DocsAskGate {
        private boolean entered;

        @Override
        public <T> T enter(String tenantId, String userId, Supplier<T> action) {
            entered = true;
            return action.get();
        }
    }
}
