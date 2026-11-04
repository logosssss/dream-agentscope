package com.zhu.bootstrap.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 用户进程启动时从 Nacos 拉 infra 与业务两份配置。地址为空跳过。
 * 配了地址却缺一份或正文空白时拒绝启动。infra 不热更；业务热更由运行时 Watcher 负责。
 * 本阶段日志系统可能未就绪，关键信息同时打到标准输出。
 */
public class NacosStartupConfigLoader implements EnvironmentPostProcessor, Ordered {

    private final DeferredLog log = new DeferredLog();

    /**
     * 低于系统环境变量（P1），高于本地 application.yml。
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String serverAddr = firstText(
                environment.getProperty("agentscope.nacos.server-addr"),
                environment.getProperty("NACOS_ADDR"),
                System.getProperty("agentscope.nacos.server-addr"),
                System.getProperty("NACOS_ADDR"),
                System.getenv("NACOS_ADDR"));
        if (serverAddr == null) {
            say("nacos skipped: NACOS_ADDR / agentscope.nacos.server-addr is blank");
            return;
        }
        say("nacos connecting, serverAddr=" + serverAddr);
        String namespace = firstText(
                environment.getProperty("agentscope.nacos.namespace"),
                environment.getProperty("NACOS_NAMESPACE"),
                System.getProperty("agentscope.nacos.namespace"),
                System.getProperty("NACOS_NAMESPACE"),
                System.getenv("NACOS_NAMESPACE"));
        String username = firstText(
                environment.getProperty("agentscope.nacos.username"),
                environment.getProperty("NACOS_USERNAME"),
                System.getProperty("agentscope.nacos.username"),
                System.getProperty("NACOS_USERNAME"),
                System.getenv("NACOS_USERNAME"));
        String password = firstText(
                environment.getProperty("agentscope.nacos.password"),
                environment.getProperty("NACOS_PASSWORD"),
                System.getProperty("agentscope.nacos.password"),
                System.getProperty("NACOS_PASSWORD"),
                System.getenv("NACOS_PASSWORD"));
        ConfigService client = null;
        try {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
            properties.setProperty(PropertyKeyConst.CONFIG_REQUEST_TIMEOUT, Long.toString(NacosStartupConfig.TIMEOUT_MS));
            if (namespace != null) {
                properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
                say("nacos namespace=" + namespace);
            } else {
                say("nacos namespace is blank, using public");
            }
            if (username != null) {
                properties.setProperty(PropertyKeyConst.USERNAME, username);
            }
            if (password != null) {
                properties.setProperty(PropertyKeyConst.PASSWORD, password);
            }
            client = NacosFactory.createConfigService(properties);
            String infra = client.getConfig(
                    NacosStartupConfig.INFRA_DATA_ID, NacosStartupConfig.GROUP, NacosStartupConfig.TIMEOUT_MS);
            String business = client.getConfig(
                    NacosStartupConfig.BUSINESS_DATA_ID, NacosStartupConfig.GROUP, NacosStartupConfig.TIMEOUT_MS);
            // 先关掉启动用客户端，避免 gRPC 连接还在时干扰后续 MySQL 握手（已复现）。
            close(client);
            client = null;
            MapPropertySource infraSource = NacosStartupConfigSupport.parseInfra(infra);
            MapPropertySource businessSource = NacosStartupConfigSupport.parseBusiness(business);
            insert(environment, infraSource);
            insert(environment, businessSource);
            Object milvusUri = infraSource.getProperty("agentscope.milvus.uri");
            String milvusText = milvusUri == null || milvusUri.toString().isBlank() ? "<blank>" : milvusUri.toString();
            // 不打印口令；只打出最终生效的 jdbc url，便于区分「没拉到」和「库连不上」。
            String jdbcUrl = environment.getProperty("spring.datasource.url");
            say("nacos infra and business startup config loaded; milvus.uri=" + milvusText
                    + "; spring.datasource.url=" + (jdbcUrl == null || jdbcUrl.isBlank() ? "<blank>" : jdbcUrl));
        } catch (RuntimeException ex) {
            say("nacos startup failed: " + ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            say("nacos startup failed: " + ex.getMessage());
            throw new IllegalStateException("nacos startup config is required", ex);
        } finally {
            close(client);
        }
    }

    /** 尽量靠前，但排在系统环境变量后面。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private void say(String message) {
        System.out.println("[NacosStartupConfigLoader] " + message);
        log.info(message);
    }

    private static void insert(ConfigurableEnvironment environment, MapPropertySource source) {
        if (environment.getPropertySources().contains(source.getName())) {
            environment.getPropertySources().replace(source.getName(), source);
            return;
        }
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
            return;
        }
        environment.getPropertySources().addFirst(source);
    }

    /** 从前到后取第一个非空白。环境变量和 -D 也直接读，避免只依赖 Environment 解析时机。 */
    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** 关掉客户端。失败只打日志，不掩盖已经抛出的启动错误。 */
    private void close(ConfigService client) {
        if (client == null) {
            return;
        }
        try {
            client.shutDown();
        } catch (Exception error) {
            say("nacos config client shutdown failed");
        }
    }
}
