package com.zhu.product.docs.tool;

import com.zhu.docs.api.DocsSearchHit;
import com.zhu.docs.api.DocsSearchQuery;
import com.zhu.docs.api.DocsSearchResult;
import com.zhu.docs.application.SearchService;
import com.zhu.semantic.contract.tool.ProductTool;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 搭子可挂的平台文档检索工具。只读，身份来自 RuntimeContext。
 * 真正检索交给 {@link SearchService}；本类只做去重、链接拼装与失败兜底。
 * 仅在 SearchService 存在时注册，避免关 MySQL 时拖垮启动。
 */
@Component
@ConditionalOnBean(SearchService.class)
public class DocsSearchTool implements ProductTool {

    private static final Logger log = Logger.getLogger(DocsSearchTool.class.getName());

    /** 与 tool_registry、@Tool name 一致。 */
    public static final String TOOL_ID = "docs_search";

    private static final int LIMIT = 5;

    private static final String NO_USER = "没有可检索的文档。";

    private static final String NO_HIT = "没有找到相关文档。";

    private final SearchService searchService;

    /**
     * @param searchService 平台文档检索；由 MySQL 打开时的 SearchService 提供
     */
    public DocsSearchTool(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 目录匹配用这个标识。 */
    @Override
    public String toolId() {
        return TOOL_ID;
    }

    /** 工具方法就在本对象上。 */
    @Override
    public Object target() {
        return this;
    }

    /**
     * 按问句检索平台文档。RuntimeContext 由框架注入，不要加 @ToolParam。
     * 同一 docId 只留第一条；失败不抛给模型线程。
     */
    @Tool(
            name = TOOL_ID,
            description = "当需要查阅本平台自己的设计文档、落地记录或架构说明时使用。输入是检索问题。")
    public String search(
            @ToolParam(name = "query", description = "检索问题") String query, RuntimeContext ctx) {
        try {
            String userId = ctx == null ? null : ctx.getUserId();
            if (userId == null || userId.isBlank()) {
                return NO_USER;
            }
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) {
                return NO_HIT;
            }
            DocsSearchResult result = searchService.search(userId, new DocsSearchQuery(q, null, null, LIMIT));
            List<DocsSearchHit> hits = result == null ? List.of() : result.hits();
            String formatted = format(hits);
            return formatted.isEmpty() ? NO_HIT : formatted;
        } catch (RuntimeException ex) {
            log.warning("docs search failed");
            return NO_HIT;
        }
    }

    /**
     * 按检索顺序拼卡片；同一 docId 只保留第一次出现。
     * 缺字段用空串，避免模型侧看到 null。
     */
    static String format(List<DocsSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder();
        int index = 0;
        for (DocsSearchHit hit : hits) {
            if (hit == null) {
                continue;
            }
            String docId = hit.docId() == null ? "" : hit.docId().trim();
            if (docId.isEmpty() || !seen.add(docId)) {
                continue;
            }
            index++;
            if (!out.isEmpty()) {
                out.append('\n');
            }
            String title = blankToEmpty(hit.title());
            String heading = blankToEmpty(hit.heading());
            String anchor = blankToEmpty(hit.anchor());
            String snippet = blankToEmpty(hit.snippet());
            out.append('【')
                    .append(index)
                    .append('】')
                    .append(title)
                    .append(" — ")
                    .append(heading)
                    .append('\n')
                    .append("/docs/doc/")
                    .append(URLEncoder.encode(docId, StandardCharsets.UTF_8))
                    .append('#')
                    .append(anchor)
                    .append('\n')
                    .append(snippet);
        }
        return out.toString();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
