package com.zhu.docs.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档源和可见性。不进 Nacos 热更白名单；改 root 或白名单要重启。
 * 环境变量覆盖本地 yml，例如 {@code DOCS_SOURCES_0_ROOT}、{@code DOCS_OWNER_USER_IDS}。
 */
@ConfigurationProperties(prefix = "docs")
public class DocsProperties {

    /** 逗号或列表都行。空名单时没有人能看私有文档，也没有人能触发同步。 */
    private List<String> ownerUserIds = new ArrayList<>();

    /** 每天兜底同步。测试里关掉，避免上下文一启动就扫磁盘。 */
    private boolean syncEnabled = true;

    /** Spring 六段 cron。默认每天 03:00。 */
    private String syncCron = "0 0 3 * * *";

    /** 03:00 用上海时区，不跟 JVM 默认时区走。 */
    private String syncZone = "Asia/Shanghai";

    /** 公开主题。树的顺序以代码里的固定七主题为准，这里只参与校验。 */
    private List<String> publicTopics = new ArrayList<>(List.of(
            "architecture",
            "agent-engineering",
            "knowledge-retrieval",
            "data-storage",
            "frontend",
            "ops"));

    /** 私有主题。默认只有 career。 */
    private List<String> privateTopics = new ArrayList<>(List.of("career"));

    /** 要扫描的仓库。顺序就是 {@code DOCS_SOURCES_n_ROOT} 的下标。 */
    private List<Source> sources = new ArrayList<>();

    /**
     * 白名单。单个元素里若有逗号，按逗号再拆开，方便环境变量写成一行。
     */
    public List<String> ownerUserIds() {
        List<String> ids = new ArrayList<>();
        for (String raw : ownerUserIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split(",")) {
                if (!part.isBlank()) {
                    ids.add(part.trim());
                }
            }
        }
        return List.copyOf(ids);
    }

    /** 主题在私有列表里就是 private，其余是 public。 */
    public String visibility(String topic) {
        return privateTopics.contains(topic) ? "private" : "public";
    }

    public void setOwnerUserIds(List<String> ownerUserIds) {
        this.ownerUserIds = ownerUserIds == null ? new ArrayList<>() : ownerUserIds;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public String getSyncCron() {
        return syncCron;
    }

    public void setSyncCron(String syncCron) {
        this.syncCron = syncCron;
    }

    public String getSyncZone() {
        return syncZone;
    }

    public void setSyncZone(String syncZone) {
        this.syncZone = syncZone;
    }

    public List<String> getPublicTopics() {
        return publicTopics;
    }

    public void setPublicTopics(List<String> publicTopics) {
        this.publicTopics = publicTopics == null ? new ArrayList<>() : publicTopics;
    }

    public List<String> getPrivateTopics() {
        return privateTopics;
    }

    public void setPrivateTopics(List<String> privateTopics) {
        this.privateTopics = privateTopics == null ? new ArrayList<>() : privateTopics;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    /**
     * 一个 git 仓库的扫描范围。
     */
    public static class Source {

        private String repo = "";

        private String root = "";

        private List<String> include = new ArrayList<>();

        private List<String> exclude = new ArrayList<>();

        public String getRepo() {
            return repo;
        }

        public void setRepo(String repo) {
            this.repo = repo;
        }

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public List<String> getInclude() {
            return include;
        }

        public void setInclude(List<String> include) {
            this.include = include == null ? new ArrayList<>() : include;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude == null ? new ArrayList<>() : exclude;
        }
    }
}
