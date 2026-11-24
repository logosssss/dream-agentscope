package com.zhu.product.docs.api;

import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.docs.api.DocsAsk;
import com.zhu.docs.api.DocsAskQuery;
import com.zhu.docs.api.DocsAskResult;
import com.zhu.docs.api.DocsIngest;
import com.zhu.docs.api.DocsPage;
import com.zhu.docs.api.DocsQuery;
import com.zhu.docs.api.DocsSearch;
import com.zhu.docs.api.DocsSearchQuery;
import com.zhu.docs.api.DocsSearchResult;
import com.zhu.docs.api.DocsSyncAccepted;
import com.zhu.docs.api.DocsSyncStatus;
import com.zhu.docs.api.DocsTree;
import com.zhu.docs.application.DocsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档产品的 HTTP 入口。路径前缀固定，方法上只写相对路径，六个对外 URL 不变。
 * 同步只给 owner-user-ids 里的用户。索引没装配时返回 UNAVAILABLE，不改走内存。
 */
@RestController
@RequestMapping("/api/docs")
public class DocsController {

    private final ObjectProvider<DocsQuery> queries;

    private final ObjectProvider<DocsIngest> ingests;

    private final ObjectProvider<DocsSearch> searches;

    private final ObjectProvider<DocsAsk> asks;

    private final DocsProperties properties;

    /** 查询、搜索、提问和同步都可能因为 MySQL 关掉而缺席。 */
    public DocsController(
            ObjectProvider<DocsQuery> queries,
            ObjectProvider<DocsIngest> ingests,
            ObjectProvider<DocsSearch> searches,
            ObjectProvider<DocsAsk> asks,
            DocsProperties properties) {
        this.queries = queries;
        this.ingests = ingests;
        this.searches = searches;
        this.asks = asks;
        this.properties = properties;
    }

    /** 七个主题。匿名只能看到公开文档。 */
    @GetMapping("/tree")
    public ApiResult<DocsTree> tree() {
        return ApiResult.ok(query().tree(UserContext.get()));
    }

    /**
     * 一篇正文。私有文档对白名单外是 NOT_FOUND，HTTP 仍按全站业务失败返回。
     */
    @GetMapping("/doc/{id}")
    public ApiResult<DocsPage> page(@PathVariable String id) {
        return ApiResult.ok(query().page(UserContext.get(), id));
    }

    /**
     * 就这一篇提问。未登录由服务拒绝。索引没装配时不可用，不改走内存。
     */
    @PostMapping("/doc/{id}/ask")
    public ApiResult<DocsAskResult> ask(
            @PathVariable String id,
            @RequestBody(required = false) DocsAskQuery query) {
        return ApiResult.ok(asker().ask(UserContext.get(), id, query));
    }

    /**
     * 语义搜索。向量没有命中时服务里改走关键词。索引没装配时不可用。
     */
    @PostMapping("/search")
    public ApiResult<DocsSearchResult> search(@RequestBody(required = false) DocsSearchQuery query) {
        return ApiResult.ok(searcher().search(UserContext.get(), query));
    }

    /**
     * 手动同步。不是所有者时按不存在处理，避免把入口暴露成 403。
     */
    @PostMapping("/sync")
    public ApiResult<DocsSyncAccepted> sync() {
        String userId = UserContext.get();
        if (userId == null || !properties.ownerUserIds().contains(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "not found");
        }
        return ApiResult.ok(new DocsSyncAccepted(ingest().start()));
    }

    /** 最近一轮。还没跑过时状态是 NONE。 */
    @GetMapping("/sync/status")
    public ApiResult<DocsSyncStatus> status() {
        return ApiResult.ok(ingest().status());
    }

    private DocsQuery query() {
        DocsQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "docs index is unavailable");
        }
        return query;
    }

    private DocsSearch searcher() {
        DocsSearch search = searches.getIfAvailable();
        if (search == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "docs search is unavailable");
        }
        return search;
    }

    private DocsAsk asker() {
        DocsAsk ask = asks.getIfAvailable();
        if (ask == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "docs ask is unavailable");
        }
        return ask;
    }

    private DocsIngest ingest() {
        DocsIngest ingest = ingests.getIfAvailable();
        if (ingest == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "docs sync is unavailable");
        }
        return ingest;
    }
}
