package com.zhu.docs.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.docs.api.DocsPage;
import com.zhu.docs.api.DocsQuery;
import com.zhu.docs.api.DocsResolvedLink;
import com.zhu.docs.api.DocsTocEntry;
import com.zhu.docs.api.DocsTopicGroup;
import com.zhu.docs.api.DocsTree;
import com.zhu.docs.api.DocsTreeItem;
import com.zhu.docs.infrastructure.MarkdownParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按身份过滤后的阅读查询。私有文档对白名单外与不存在是同一种结果。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class DocsQueryService implements DocsQuery {

    private final DocsCatalog catalog;

    private final DocsProperties properties;

    private final DocsLinkResolver links = new DocsLinkResolver(new MarkdownParser());

    /** 可见性规则来自配置里的私有主题，默认只有 career。链接在读的时候算。 */
    public DocsQueryService(DocsCatalog catalog, DocsProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * 七个主题都返回。匿名只能看到 public。
     */
    @Override
    public DocsTree tree(String userId) {
        boolean owner = isOwner(userId);
        List<CatalogDocument> visible = new ArrayList<>(catalog.listVisible(owner));
        visible.sort(Comparator.comparing(CatalogDocument::title, Comparator.nullsLast(String::compareTo)));
        List<DocsTopicGroup> groups = new ArrayList<>();
        for (String topic : DocsTopicClassifier.TOPICS) {
            List<DocsTreeItem> items = new ArrayList<>();
            for (CatalogDocument document : visible) {
                if (!topic.equals(document.topic())) {
                    continue;
                }
                items.add(new DocsTreeItem(
                        document.id(),
                        document.title(),
                        document.docType(),
                        document.visibility(),
                        document.source()));
            }
            groups.add(new DocsTopicGroup(topic, List.copyOf(items)));
        }
        return new DocsTree(List.copyOf(groups));
    }

    /**
     * 找不到或不能看都是 NOT_FOUND。不告诉调用方这篇是私有的。
     */
    @Override
    public DocsPage page(String userId, String id) {
        boolean owner = isOwner(userId);
        CatalogDocument document = catalog.findVisible(id, owner)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "document not found"));
        List<DocsTocEntry> toc = new ArrayList<>();
        for (CatalogChunk chunk : document.chunks()) {
            toc.add(new DocsTocEntry(chunk.heading(), chunk.anchor()));
        }
        String markdown = document.markdown() == null ? "" : document.markdown();
        List<DocsResolvedLink> resolved = links.resolve(
                document.path(), markdown, catalog.linkTargets(document.repo(), owner));
        return new DocsPage(
                document.title(),
                document.topic(),
                markdown,
                List.copyOf(toc),
                document.indexedAt(),
                resolved);
    }

    /** 空用户不是所有者。名单为空时没有任何所有者。 */
    public boolean isOwner(String userId) {
        return userId != null && !userId.isBlank() && properties.ownerUserIds().contains(userId);
    }
}
