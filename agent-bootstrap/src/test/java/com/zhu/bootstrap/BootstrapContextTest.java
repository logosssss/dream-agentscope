package com.zhu.bootstrap;

import com.zhu.runtime.api.AgentExecutor;
import io.agentscope.harness.agent.DistributedStore;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BootstrapContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Test
    void startsWithoutAgent() throws Exception {
        assertThat(context.getBeanNamesForType(AgentExecutor.class)).isEmpty();
        assertThat(context.getBeansOfType(DistributedStore.class)).isEmpty();
        assertThat(context.getBeanDefinitionNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("harnessagent"));
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
