package com.zhu.docs.application;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 用目录和文件名给文档打主题，不调用模型。
 * 打不中的落到 architecture，并记一条日志。
 */
public final class DocsTopicClassifier {

    /** 固定七个主题。树接口按这个顺序返回。 */
    public static final List<String> TOPICS = List.of(
            "architecture",
            "agent-engineering",
            "knowledge-retrieval",
            "data-storage",
            "frontend",
            "ops",
            "career");

    private static final Logger LOG = Logger.getLogger(DocsTopicClassifier.class.getName());

    private static final List<Rule> RULES = List.of(
            new Rule("frontend", List.of("前端", "frontend", "/web/")),
            new Rule("ops", List.of("部署", "运维", "release", "ops")),
            new Rule("data-storage", List.of("缓存", "存储", "cache", "flyway", "mysql", "milvus", "pgvector")),
            new Rule("knowledge-retrieval", List.of("向量", "检索", "知识", "rag", "embedding", "knowledge")),
            new Rule("agent-engineering", List.of(
                    "agent", "harness", "mcp", "sandbox", "wakeup", "permission",
                    "channel", "memory", "subagent", "skill")),
            new Rule("architecture", List.of("架构", "adr", "packaging", "handbook", "diagram", "readme")));

    private DocsTopicClassifier() {
    }

    /**
     * 先看 dream-saas 的两个私有目录，再按关键词。career 不会因为文件名里有「面试」被公开目录命中。
     *
     * @param repo 仓库键
     * @param path 相对路径，用 {@code /}
     */
    public static String topic(String repo, String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        if ("dream-saas".equals(repo)
                && (normalized.startsWith("docs/03-面试准备/") || normalized.startsWith("docs/11-求职/"))) {
            return "career";
        }
        String haystack = normalized.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.matches(normalized, haystack)) {
                return rule.topic;
            }
        }
        if (normalized.startsWith("web/")) {
            return "frontend";
        }
        LOG.warning("docs topic fallback repo=" + repo + " path=" + normalized);
        return "architecture";
    }

    /**
     * 路径种类，不参与能不能看。
     *
     * @param path 相对路径
     */
    public static String docType(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if ("README.md".equalsIgnoreCase(name)) {
            return "readme";
        }
        if (normalized.contains("/handbook/")) {
            return "handbook";
        }
        if (normalized.contains("/tutorial/")) {
            return "tutorial";
        }
        if (normalized.contains("/reference/") || normalized.contains("/diagrams/")) {
            return "reference";
        }
        if (name.endsWith("落地.md")) {
            return "landing";
        }
        return "guide";
    }

    /** 一条关键词规则。中文用原文，英文用已经小写的路径。 */
    private record Rule(String topic, List<String> needles) {

        private boolean matches(String raw, String lower) {
            for (String needle : needles) {
                if (needle.codePoints().anyMatch(code -> code > 127)) {
                    if (raw.contains(needle)) {
                        return true;
                    }
                } else if (lower.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }
}
