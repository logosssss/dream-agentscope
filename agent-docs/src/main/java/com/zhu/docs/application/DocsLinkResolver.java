package com.zhu.docs.application;

import com.zhu.docs.api.DocsResolvedLink;
import com.zhu.docs.infrastructure.AnchorSlug;
import com.zhu.docs.infrastructure.MarkdownLink;
import com.zhu.docs.infrastructure.MarkdownParser;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把一篇里的同仓相对 .md 链接收成站内地址。
 * id 用库里已经可见的路径对上，不在这里重新哈希，免得链到没进索引或当前身份看不见的文件。
 */
public final class DocsLinkResolver {

    private final MarkdownParser parser;

    /** 解析器只抽链接，不负责知道别的文件在不在。 */
    public DocsLinkResolver(MarkdownParser parser) {
        this.parser = parser;
    }

    /**
     * 按正文出现顺序给出替换表。同一 target 只留第一条。
     * 图片、外链、越出仓库根的路径和库里没有的文件都不出现，前端就保持原样。
     *
     * @param fromPath 当前文档的相对路径
     * @param markdown 原文，可以是 null
     * @param targets  同仓可见文档，可以是空
     */
    public List<DocsResolvedLink> resolve(String fromPath, String markdown, List<DocsLinkTarget> targets) {
        Map<String, DocsLinkTarget> byPath = index(targets);
        List<DocsResolvedLink> resolved = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (MarkdownLink link : parser.parse(markdown).links()) {
            String target = link.target();
            if (target == null || seen.containsKey(target)) {
                continue;
            }
            String href = href(fromPath, target, byPath);
            if (href == null) {
                continue;
            }
            seen.put(target, href);
            resolved.add(new DocsResolvedLink(target, href));
        }
        return List.copyOf(resolved);
    }

    /** 路径按小写索引。Windows 上大小写不同的写法应落到同一篇。 */
    private static Map<String, DocsLinkTarget> index(List<DocsLinkTarget> targets) {
        Map<String, DocsLinkTarget> byPath = new LinkedHashMap<>();
        if (targets == null) {
            return byPath;
        }
        for (DocsLinkTarget target : targets) {
            if (target == null || target.id() == null || target.path() == null || !safeId(target.id())) {
                continue;
            }
            String key = target.path().replace('\\', '/').toLowerCase(Locale.ROOT);
            byPath.putIfAbsent(key, target);
        }
        return byPath;
    }

    /**
     * 算出 {@code /docs/doc/{id}}。没有对应可见文档时返回 null。
     * 片段先对上目标文档已有锚点，对不上再用同一套标题规则收成锚点。
     */
    private static String href(String fromPath, String rawTarget, Map<String, DocsLinkTarget> byPath) {
        int hash = rawTarget.indexOf('#');
        String pathPart = hash >= 0 ? rawTarget.substring(0, hash) : rawTarget;
        String fragment = hash >= 0 ? rawTarget.substring(hash + 1) : "";
        int query = pathPart.indexOf('?');
        if (query >= 0) {
            pathPart = pathPart.substring(0, query);
        }
        String normalized = normalize(fromPath, decode(pathPart));
        if (normalized == null) {
            return null;
        }
        DocsLinkTarget found = byPath.get(normalized.toLowerCase(Locale.ROOT));
        if (found == null) {
            return null;
        }
        String anchor = anchor(decode(fragment), found.anchors());
        String href = "/docs/doc/" + url(found.id());
        if (!anchor.isEmpty()) {
            href = href + "#" + url(anchor);
        }
        return href;
    }

    /**
     * 相对当前文件的目录拼路径，再消掉 {@code .} 和 {@code ..}。
     * 不是 .md、或 {@code ..} 跳出仓库根时返回 null。
     */
    static String normalize(String fromPath, String relative) {
        if (relative == null) {
            return null;
        }
        String spec = relative.trim().replace('\\', '/');
        if (spec.isEmpty() || spec.startsWith("/") || !spec.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return null;
        }
        String base = parent(fromPath == null ? "" : fromPath.replace('\\', '/'));
        String combined = base.isEmpty() ? spec : base + "/" + spec;
        List<String> stack = new ArrayList<>();
        for (String part : combined.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (stack.isEmpty()) {
                    return null;
                }
                stack.remove(stack.size() - 1);
                continue;
            }
            stack.add(part);
        }
        if (stack.isEmpty()) {
            return null;
        }
        return String.join("/", stack);
    }

    /** 文件所在目录。根上的 README 没有目录。 */
    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return path.substring(0, slash);
    }

    /**
     * 片段已经是目标文档的锚点就原样用，这样重复标题的 {@code -1} 不会被重新编号。
     * 否则按标题规则转一次。空片段表示链接没有锚点。
     */
    private static String anchor(String fragment, List<String> anchors) {
        if (fragment == null || fragment.isBlank()) {
            return "";
        }
        String trimmed = fragment.trim();
        if (anchors != null && anchors.contains(trimmed)) {
            return trimmed;
        }
        return AnchorSlug.base(trimmed);
    }

    /** 只解码百分号转义。字面量 {@code +} 不是空格，不能交给 {@link URLDecoder}。 */
    private static String decode(String value) {
        if (value == null || value.isEmpty() || !value.contains("%")) {
            return value == null ? "" : value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    /** 路径和片段里的空格、汉字要进 URL。id 本身是安全字符时编码结果不变。 */
    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** id 进 URL 路径。斜杠和问号会把地址拆开，这种 id 不生成链接。 */
    private static boolean safeId(String id) {
        return !id.isBlank()
                && id.indexOf('/') < 0
                && id.indexOf('\\') < 0
                && id.indexOf('?') < 0
                && id.indexOf('#') < 0
                && id.indexOf(' ') < 0;
    }
}
