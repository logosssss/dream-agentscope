package com.zhu.docs.application;

import com.zhu.docs.application.DocsProperties.Source;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档源的包内默认收录规则。环境变量可以只改 root，空的仓库名和规则从这里补。
 */
public final class DocsSourceMerge {

    private DocsSourceMerge() {
    }

    /**
     * 按下标合并。绑定结果里非空的字段保留，空字段用包内默认。
     * 绑定结果比默认更长时，多出来的条目原样留下。
     */
    public static List<Source> merge(List<Source> bound) {
        List<Source> defaults = packaged();
        List<Source> incoming = bound == null ? List.of() : bound;
        int size = Math.max(incoming.size(), defaults.size());
        List<Source> merged = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Source fromEnv = i < incoming.size() ? incoming.get(i) : new Source();
            Source fallback = i < defaults.size() ? defaults.get(i) : new Source();
            Source source = new Source();
            source.setRepo(firstText(fromEnv.getRepo(), fallback.getRepo()));
            source.setRoot(firstText(fromEnv.getRoot(), fallback.getRoot()));
            source.setInclude(firstList(fromEnv.getInclude(), fallback.getInclude()));
            source.setExclude(firstList(fromEnv.getExclude(), fallback.getExclude()));
            if (source.getRepo().isBlank() && source.getRoot().isBlank()) {
                continue;
            }
            merged.add(source);
        }
        return List.copyOf(merged);
    }

    /**
     * 与用户进程 application.yml 的两条源一致。本地 root 是开发机路径，线上只改 root。
     */
    static List<Source> packaged() {
        Source agentscope = source(
                "agentscope",
                "E:/dream-agentscope",
                List.of("docs/**/*.md", "README.md", "agent-*/README.md"),
                List.of(".agents/**", "**/target/**", "**/node_modules/**"));
        Source dreamSaas = source(
                "dream-saas",
                "E:/dream-saas",
                List.of(
                        "docs/01-技术架构/**/*.md",
                        "docs/03-面试准备/**/*.md",
                        "docs/11-求职/**/*.md"),
                List.of("docs/04-学习路线/**", "docs/06-文章内容/**"));
        return List.of(agentscope, dreamSaas);
    }

    /** 拼一条包内默认源。列表按传入的不可变副本保存。 */
    private static Source source(String repo, String root, List<String> include, List<String> exclude) {
        Source source = new Source();
        source.setRepo(repo);
        source.setRoot(root);
        source.setInclude(include);
        source.setExclude(exclude);
        return source;
    }

    /** 绑定值空白时用默认。空白含 null 和只有空格。 */
    private static String firstText(String bound, String fallback) {
        if (bound != null && !bound.isBlank()) {
            return bound.trim();
        }
        return fallback == null ? "" : fallback;
    }

    /** 绑定列表有内容时用绑定的。空列表视为没写，用默认。 */
    private static List<String> firstList(List<String> bound, List<String> fallback) {
        if (bound != null && bound.stream().anyMatch(item -> item != null && !item.isBlank())) {
            return List.copyOf(bound);
        }
        return fallback == null ? List.of() : List.copyOf(fallback);
    }
}
