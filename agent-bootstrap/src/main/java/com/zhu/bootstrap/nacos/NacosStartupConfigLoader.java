package com.zhu.bootstrap.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import java.util.Properties;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 用户进程启动时从 Nacos 拉业务旋钮。地址为空跳过。
 * 配了地址却拉不到或正文空白时拒绝启动。不做热更新、服务发现和 MCP。
 */
public class NacosStartupConfigLoader implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = Logger.getLogger(NacosStartupConfigLoader.class.getName());

    /**
     * 在本地 application.yml 之后合并，低于系统环境变量，避免配置中心盖掉机器上的密钥。
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String serverAddr = firstText(
                environment.getProperty("agentscope.nacos.server-addr"),
                environment.getProperty("NACOS_ADDR"));
        if (serverAddr == null) {
            return;
        }
        String namespace = firstText(
                environment.getProperty("agentscope.nacos.namespace"),
                environment.getProperty("NACOS_NAMESPACE"));
        String username = firstText(
                environment.getProperty("agentscope.nacos.username"),
                environment.getProperty("NACOS_USERNAME"));
        String password = firstText(
                environment.getProperty("agentscope.nacos.password"),
                environment.getProperty("NACOS_PASSWORD"));
        ConfigService client = null;
        try {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
            properties.setProperty(PropertyKeyConst.CONFIG_REQUEST_TIMEOUT, Long.toString(NacosStartupConfig.TIMEOUT_MS));
            if (namespace != null) {
                properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
            }
            if (username != null) {
                properties.setProperty(PropertyKeyConst.USERNAME, username);
            }
            if (password != null) {
                properties.setProperty(PropertyKeyConst.PASSWORD, password);
            }
            client = NacosFactory.createConfigService(properties);
            String content = client.getConfig(
                    NacosStartupConfig.DATA_ID, NacosStartupConfig.GROUP, NacosStartupConfig.TIMEOUT_MS);
            MapPropertySource source = NacosStartupConfigSupport.parse(content);
            insert(environment, source);
            log.info("nacos startup config loaded");
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
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

    private static void insert(ConfigurableEnvironment environment, MapPropertySource source) {
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
            return;
        }
        environment.getPropertySources().addFirst(source);
    }

    private static String firstText(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return null;
    }

    /** 关掉客户端。失败只打日志，不掩盖已经抛出的启动错误。 */
    private static void close(ConfigService client) {
        if (client == null) {
            return;
        }
        try {
            client.shutDown();
        } catch (Exception error) {
            log.warning("nacos config client shutdown failed");
        }
    }
}
