package com.zhu.product.docs.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhu.docs.api.DocsSearchHit;
import com.zhu.docs.api.DocsSearchQuery;
import com.zhu.docs.api.DocsSearchResult;
import com.zhu.docs.application.SearchService;
import io.agentscope.core.agent.RuntimeContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 文档检索工具：命中格式、同文档去重、缺身份与异常兜底。
 */
class DocsSearchToolTest {

    @Test
    void formatsHitWithTitleHeadingPathAndAnchor() {
        SearchService searchService = mock(SearchService.class);
        when(searchService.search(eq("u1"), any(DocsSearchQuery.class)))
                .thenReturn(new DocsSearchResult(List.of(
                        new DocsSearchHit(
                                "approval-hard-delete-落地",
                                "审批硬删落地",
                                "为什么删表",
                                "why-drop",
                                "审批表已硬删",
                                0.9))));
        DocsSearchTool tool = new DocsSearchTool(searchService);
        RuntimeContext ctx = mock(RuntimeContext.class);
        when(ctx.getUserId()).thenReturn("u1");

        String out = tool.search("审批表为什么删了", ctx);

        assertThat(out).contains("审批硬删落地");
        assertThat(out).contains("为什么删表");
        assertThat(out).contains("/docs/doc/");
        assertThat(out).contains("#why-drop");
        assertThat(out).contains("审批表已硬删");
    }

    @Test
    void keepsOnlyFirstHitPerDocId() {
        SearchService searchService = mock(SearchService.class);
        when(searchService.search(eq("u1"), any(DocsSearchQuery.class)))
                .thenReturn(new DocsSearchResult(List.of(
                        new DocsSearchHit("doc-a", "标题A", "节1", "a1", "片段1", 0.9),
                        new DocsSearchHit("doc-a", "标题A", "节2", "a2", "片段2", 0.8),
                        new DocsSearchHit("doc-b", "标题B", "节B", "b1", "片段B", 0.7))));
        DocsSearchTool tool = new DocsSearchTool(searchService);
        RuntimeContext ctx = mock(RuntimeContext.class);
        when(ctx.getUserId()).thenReturn("u1");

        String out = tool.search("去重", ctx);

        assertThat(out).contains("【1】标题A — 节1");
        assertThat(out).contains("【2】标题B — 节B");
        assertThat(out).doesNotContain("节2");
        assertThat(out).doesNotContain("【3】");
    }

    @Test
    void blankUserIdReturnsNoUserFallback() {
        SearchService searchService = mock(SearchService.class);
        DocsSearchTool tool = new DocsSearchTool(searchService);

        assertThat(tool.search("任意问题", null)).isEqualTo("没有可检索的文档。");
        RuntimeContext blank = mock(RuntimeContext.class);
        when(blank.getUserId()).thenReturn("  ");
        assertThat(tool.search("任意问题", blank)).isEqualTo("没有可检索的文档。");
        verifyNoInteractions(searchService);
    }

    @Test
    void blankQueryReturnsNoHitWithoutCallingSearch() {
        SearchService searchService = mock(SearchService.class);
        DocsSearchTool tool = new DocsSearchTool(searchService);
        RuntimeContext ctx = mock(RuntimeContext.class);
        when(ctx.getUserId()).thenReturn("u1");

        assertThat(tool.search("  ", ctx)).isEqualTo("没有找到相关文档。");
        verifyNoInteractions(searchService);
    }

    @Test
    void searchFailureReturnsNoHitFallback() {
        SearchService searchService = mock(SearchService.class);
        when(searchService.search(eq("u1"), any(DocsSearchQuery.class)))
                .thenThrow(new IllegalStateException("boom"));
        DocsSearchTool tool = new DocsSearchTool(searchService);
        RuntimeContext ctx = mock(RuntimeContext.class);
        when(ctx.getUserId()).thenReturn("u1");

        assertThat(tool.search("会失败的问题", ctx)).isEqualTo("没有找到相关文档。");
    }
}
