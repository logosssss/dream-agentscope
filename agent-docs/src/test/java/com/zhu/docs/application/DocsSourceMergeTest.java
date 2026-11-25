package com.zhu.docs.application;

import com.zhu.docs.application.DocsProperties.Source;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只覆盖文档根目录时，仓库名和收录规则仍在。不启动 Spring。
 */
class DocsSourceMergeTest {

    /** 两条源都只给了 Linux 根目录。规则回到包内默认。 */
    @Test
    void rootOnlyKeepsPackagedRules() {
        Source agentscope = new Source();
        agentscope.setRoot("/opt/dream-docs/agentscope");
        Source dreamSaas = new Source();
        dreamSaas.setRoot("/opt/dream-docs/dream-saas");

        List<Source> merged = DocsSourceMerge.merge(List.of(agentscope, dreamSaas));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).getRepo()).isEqualTo("agentscope");
        assertThat(merged.get(0).getRoot()).isEqualTo("/opt/dream-docs/agentscope");
        assertThat(merged.get(0).getInclude()).containsExactly(
                "docs/**/*.md", "README.md", "agent-*/README.md");
        assertThat(merged.get(0).getExclude()).contains("**/node_modules/**");
        assertThat(merged.get(1).getRepo()).isEqualTo("dream-saas");
        assertThat(merged.get(1).getRoot()).isEqualTo("/opt/dream-docs/dream-saas");
        assertThat(merged.get(1).getInclude()).contains("docs/11-求职/**/*.md");
    }

    /** 环境变量写全了收录规则时，不退回包内默认。 */
    @Test
    void explicitIncludeStays() {
        Source agentscope = new Source();
        agentscope.setRepo("agentscope");
        agentscope.setRoot("/opt/custom");
        agentscope.setInclude(List.of("README.md"));
        agentscope.setExclude(List.of("skip/**"));

        List<Source> merged = DocsSourceMerge.merge(List.of(agentscope));

        assertThat(merged.get(0).getInclude()).containsExactly("README.md");
        assertThat(merged.get(0).getExclude()).containsExactly("skip/**");
        assertThat(merged.get(0).getRoot()).isEqualTo("/opt/custom");
        assertThat(merged.get(1).getRepo()).isEqualTo("dream-saas");
        assertThat(merged.get(1).getRoot()).isEqualTo("E:/dream-saas");
    }

    /**
     * Spring 绑定只收到更高优先级的 root 时，这一条的 include 会被换成空。
     * 合并后仓库名和收录规则还在，root 用环境变量的。
     */
    @Test
    void springBindRootOnlyThenMerge() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("docs.sources[0].repo", "agentscope");
        yaml.put("docs.sources[0].root", "E:/dream-agentscope");
        yaml.put("docs.sources[0].include[0]", "docs/**/*.md");
        yaml.put("docs.sources[0].include[1]", "README.md");
        yaml.put("docs.sources[0].include[2]", "agent-*/README.md");
        yaml.put("docs.sources[0].exclude[0]", ".agents/**");
        Map<String, Object> env = Map.of("docs.sources[0].root", "/opt/dream-docs/agentscope");
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource("env", env));
        propertySources.addLast(new MapPropertySource("yaml", yaml));
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));

        List<Source> bound = binder.bind("docs.sources", Bindable.listOf(Source.class))
                .orElseThrow(IllegalStateException::new);
        List<Source> merged = DocsSourceMerge.merge(bound);

        assertThat(bound.get(0).getInclude()).isEmpty();
        assertThat(merged.get(0).getRepo()).isEqualTo("agentscope");
        assertThat(merged.get(0).getRoot()).isEqualTo("/opt/dream-docs/agentscope");
        assertThat(merged.get(0).getInclude()).contains("docs/**/*.md");
    }
}
