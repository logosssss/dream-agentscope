package com.zhu.bootstrap.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.zhu.access.application.chat.gateway.ResidentAgentReloader;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.config.ClientAuthProperties;
import com.zhu.access.config.WxMiniProperties;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.semantic.config.PermissionProperties;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

/**
 * 监听用户进程业务 yaml。合法变更覆盖属性源并 rebind Properties；非法推送保留上一份（R1）。
 * rebind 成功后通知接入层排空重建长驻 Agent，使 model / 轮数 / 权限等对长驻实例生效。
 * infra 不在这里听。用 ApplicationRunner 且靠后执行：等 DataSource/Flyway 建连完成后再开 Nacos gRPC。
 */
@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${agentscope.nacos.server-addr:}')")
public class NacosBusinessConfigWatcher implements ApplicationRunner, Ordered {

    private static final Logger log = Logger.getLogger(NacosBusinessConfigWatcher.class.getName());

    private final ConfigurableEnvironment environment;

    private final AccessLimitProperties accessLimits;

    private final AgentRuntimeProperties runtime;

    private final PermissionProperties permissions;

    private final WxMiniProperties wxMini;

    private final ClientAuthProperties auth;

    private final ObjectProvider<ResidentAgentReloader> residentAgents;

    private final AtomicReference<ConfigService> client = new AtomicReference<>();

    private final AtomicReference<Listener> listener = new AtomicReference<>();

    /**
     * 只保存依赖。真正连 Nacos 放到 {@link #run(ApplicationArguments)}，排在基础设施之后。
     * Reloader 未装配时热更只改 Properties。
     */
    public NacosBusinessConfigWatcher(
            ConfigurableEnvironment environment,
            AccessLimitProperties accessLimits,
            AgentRuntimeProperties runtime,
            PermissionProperties permissions,
            WxMiniProperties wxMini,
            ClientAuthProperties auth,
            ObjectProvider<ResidentAgentReloader> residentAgents) {
        this.environment = environment;
        this.accessLimits = accessLimits;
        this.runtime = runtime;
        this.permissions = permissions;
        this.wxMini = wxMini;
        this.auth = auth;
        this.residentAgents = residentAgents;
    }

    /**
     * 晚于 Flyway / 就绪探测一类 Order(0) 的 runner。连不上 Nacos 仍让启动失败。
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        String serverAddr = text(environment.getProperty("agentscope.nacos.server-addr"));
        if (serverAddr == null) {
            throw new IllegalStateException("nacos server-addr is blank");
        }
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.setProperty(PropertyKeyConst.CONFIG_REQUEST_TIMEOUT, Long.toString(NacosStartupConfig.TIMEOUT_MS));
        putIfPresent(properties, PropertyKeyConst.NAMESPACE, environment.getProperty("agentscope.nacos.namespace"));
        putIfPresent(properties, PropertyKeyConst.USERNAME, environment.getProperty("agentscope.nacos.username"));
        putIfPresent(properties, PropertyKeyConst.PASSWORD, environment.getProperty("agentscope.nacos.password"));
        ConfigService configService = NacosFactory.createConfigService(properties);
        Listener configListener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                onChange(configInfo);
            }
        };
        configService.addListener(
                NacosStartupConfig.BUSINESS_DATA_ID, NacosStartupConfig.GROUP, configListener);
        this.client.set(configService);
        this.listener.set(configListener);
        log.info("nacos business config watcher started");
    }

    /** 排在 RequiredInfrastructureRunner（Order 0）之后。 */
    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * 解析失败或白名单外键整包时回滚。rebind 异常同样回滚。
     */
    void onChange(String configInfo) {
        MapPropertySource previous = snapshot();
        try {
            MapPropertySource next = NacosStartupConfigSupport.parseBusinessRefresh(configInfo);
            replace(next);
            rebind();
            rebuildResidents();
            log.info("nacos business config refreshed");
        } catch (RuntimeException ex) {
            if (previous != null) {
                replace(previous);
                try {
                    rebind();
                } catch (RuntimeException ignored) {
                    log.warning("nacos business config rollback rebind failed");
                }
            }
            log.warning("nacos business config refresh rejected: " + ex.getMessage());
        }
    }

    /** 长驻 Gateway 尚未装配时跳过。重建失败不回滚已生效的 Properties。 */
    private void rebuildResidents() {
        ResidentAgentReloader reloader = residentAgents.getIfAvailable();
        if (reloader == null) {
            return;
        }
        try {
            reloader.rebuildAll();
        } catch (RuntimeException ex) {
            log.warning("resident agent rebuild after nacos refresh failed: " + ex.getMessage());
        }
    }

    private MapPropertySource snapshot() {
        if (!(environment.getPropertySources().get(NacosStartupConfig.BUSINESS_PROPERTY_SOURCE)
                instanceof MapPropertySource current)) {
            return null;
        }
        Map<String, Object> copy = new HashMap<>();
        for (String name : current.getPropertyNames()) {
            copy.put(name, current.getProperty(name));
        }
        return new MapPropertySource(NacosStartupConfig.BUSINESS_PROPERTY_SOURCE, copy);
    }

    private void replace(MapPropertySource source) {
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(source.getName())) {
            sources.replace(source.getName(), source);
        } else {
            sources.addFirst(source);
        }
    }

    /** 把 Environment 重新绑到可热更的 Properties Bean。 */
    private void rebind() {
        Binder binder = Binder.get(environment);
        binder.bind("agentscope.access", Bindable.ofInstance(accessLimits));
        binder.bind("agentscope.runtime", Bindable.ofInstance(runtime));
        binder.bind("agentscope.permission", Bindable.ofInstance(permissions));
        binder.bind("agentscope.wx.mini", Bindable.ofInstance(wxMini));
        binder.bind("agentscope.auth", Bindable.ofInstance(auth));
        // 热更后立刻编译一遍，坏规则当场拒绝并触发外层回滚
        compilePermissions(permissions);
    }

    /** 与 ConfiguredPermissionService 同口径校验，避免脏规则留在内存。 */
    static void compilePermissions(PermissionProperties properties) {
        if (properties == null || properties.getRules() == null) {
            return;
        }
        for (int i = 0; i < properties.getRules().size(); i++) {
            PermissionProperties.Rule rule = properties.getRules().get(i);
            if (rule == null || rule.getToolId() == null || rule.getToolId().isBlank()) {
                throw new IllegalStateException("permission rule " + i + " requires tool-id");
            }
            if (rule.getTenantId() == null || rule.getTenantId().isBlank()) {
                throw new IllegalStateException("permission rule " + i + " requires tenant-id");
            }
            if (rule.getRoleId() == null || rule.getRoleId().isBlank()) {
                throw new IllegalStateException("permission rule " + i + " requires role-id");
            }
            if (rule.getDecision() == null) {
                throw new IllegalStateException("permission rule " + i + " requires decision");
            }
            try {
                com.zhu.semantic.contract.permission.PermissionDecision.valueOf(rule.getDecision());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("permission rule " + i + " has unknown decision");
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        ConfigService configService = client.get();
        Listener configListener = listener.get();
        if (configService == null) {
            return;
        }
        try {
            if (configListener != null) {
                configService.removeListener(
                        NacosStartupConfig.BUSINESS_DATA_ID, NacosStartupConfig.GROUP, configListener);
            }
            configService.shutDown();
        } catch (Exception ex) {
            log.warning("nacos business config watcher shutdown failed");
        }
    }

    private static void putIfPresent(Properties properties, String key, String value) {
        String text = text(value);
        if (text != null) {
            properties.setProperty(key, text);
        }
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
