package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.runtime.api.agent.AgentImagePart;
import com.zhu.runtime.api.agent.AgentRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 看图失败只在有图时改成纯文本。不启动 Gateway。
 */
class VisionDegradeTest {

    /** 没图时原错误继续抛出。有图时正文带上降级前缀，并且不写 name。 */
    @Test
    void retriesOnlyWhenImagesExist() {
        AgentRequest text = new AgentRequest("companion-v1", "t", "u", "s", "", "hello");
        IllegalStateException error = new IllegalStateException("down");
        assertThatThrownBy(() -> VisionDegrade.plainOrThrow(text, error)).isSameAs(error);

        AgentRequest image = new AgentRequest(
                "companion-v1", "t", "u", "s", "", "hello",
                List.of(new AgentImagePart("a", "image/png", "aaaa")));
        var plain = VisionDegrade.plainOrThrow(image, error);
        assertThat(plain.getName()).isNull();
        assertThat(plain.getTextContent()).startsWith(AgentUserMessages.VISION_DEGRADE_PREFIX);
        assertThat(plain.getTextContent()).contains("hello");
    }
}
