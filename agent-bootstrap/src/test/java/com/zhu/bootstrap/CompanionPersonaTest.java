package com.zhu.bootstrap;

import com.zhu.semantic.config.CompanionPersona;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 搭子人格锁定在代码里。不把 userId 写进 agentId。
 */
class CompanionPersonaTest {

    /** 版本、关系和禁区都是固定值。 */
    @Test
    void identityIsLockedToVersionOne() {
        CompanionPersona persona = new CompanionPersona();
        assertThat(persona.agentId()).isEqualTo("companion-v1");
        assertThat(persona.agentId()).doesNotContain(":");
        assertThat(persona.relationshipStage()).isEqualTo("ACQUAINTANCE");
        assertThat(persona.coreIdentity()).contains("智能搭子");
        assertThat(persona.boundaries()).isNotEmpty();
    }
}
