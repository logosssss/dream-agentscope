package com.zhu.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 检索条件必须同时限制用户和 Agent。不连接 Milvus。
 */
class KnowledgeFilterTest {

    /** 引号被转义，条件仍是并且关系。 */
    @Test
    void filterRequiresBoth() {
        assertThat(KnowledgeFilter.of("user\"a", "agent\\b"))
                .isEqualTo("user_id == \"user\\\"a\" && agent_id == \"agent\\\\b\"");
    }

    /** 缺用户时不生成条件。 */
    @Test
    void blankUserIsRejected() {
        assertThatThrownBy(() -> KnowledgeFilter.of(" ", "agent-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
