package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 主题只看目录和文件名。career 只来自两个私有目录。 */
class DocsTopicClassifierTest {

    /** 技术架构目录里的面试材料仍是公开主题。 */
    @Test
    void interviewInsideArchitectureStaysPublic() {
        assertThat(DocsTopicClassifier.topic("dream-saas", "docs/01-技术架构/reference/面试QA速查.md"))
                .isEqualTo("architecture");
        assertThat(DocsTopicClassifier.topic("dream-saas", "docs/03-面试准备/自我介绍.md"))
                .isEqualTo("career");
        assertThat(DocsTopicClassifier.topic("dream-saas", "docs/11-求职/简历.md"))
                .isEqualTo("career");
    }

    /** 关键词按约定的顺序，先命中的主题生效。 */
    @Test
    void keywordOrder() {
        assertThat(DocsTopicClassifier.topic("agentscope", "docs/cache-落地.md")).isEqualTo("data-storage");
        assertThat(DocsTopicClassifier.topic("agentscope", "docs/mcp-落地.md")).isEqualTo("agent-engineering");
        assertThat(DocsTopicClassifier.topic("agentscope", "agent-runtime/README.md"))
                .isEqualTo("agent-engineering");
        assertThat(DocsTopicClassifier.topic("agentscope", "README.md")).isEqualTo("architecture");
    }

    /** 角标和主题分开。 */
    @Test
    void docTypeFollowsPath() {
        assertThat(DocsTopicClassifier.docType("README.md")).isEqualTo("readme");
        assertThat(DocsTopicClassifier.docType("docs/handbook/01-repo-map.md")).isEqualTo("handbook");
        assertThat(DocsTopicClassifier.docType("docs/cache-落地.md")).isEqualTo("landing");
        assertThat(DocsTopicClassifier.docType("docs/01-技术架构/介绍.md")).isEqualTo("guide");
    }
}
