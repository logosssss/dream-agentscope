package com.zhu.semantic.infrastructure.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import java.util.Properties;
import java.util.logging.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 产品提示词监听。只在配了 Nacos 地址时注册，和缺省来源互斥。
 * 不管动态配置、服务发现和 MCP。拉不到由 PromptService 退回兜底，这里不让进程退出。
 */
@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${agentscope.nacos.server-addr:}')")
public class NacosPromptSource implements PromptSource, DisposableBean {

    private static final Logger log = Logger.getLogger(NacosPromptSource.class.getName());

    /** 单次拉取超时。超时后调用方用兜底，不把对话卡住。 */
    private static final String REQUEST_TIMEOUT_MS = "3000";

    private final AiService ai;

    private final NacosPromptListener listener;

    /**
     * 地址、命名空间和账号都来自环境变量。命名空间或账号为空就不传。
     * 客户端建不起来时退回缺省行为，不把地址和口令写进日志。
     */
    public NacosPromptSource(
            @Value("${agentscope.nacos.server-addr:}") String serverAddr,
            @Value("${agentscope.nacos.namespace:}") String namespace,
            @Value("${agentscope.nacos.username:}") String username,
            @Value("${agentscope.nacos.password:}") String password) {
        AiService created = null;
        NacosPromptListener prompts = null;
        try {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
            properties.setProperty(PropertyKeyConst.CONFIG_REQUEST_TIMEOUT, REQUEST_TIMEOUT_MS);
            if (namespace != null && !namespace.isBlank()) {
                properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
            }
            if (username != null && !username.isBlank()) {
                properties.setProperty(PropertyKeyConst.USERNAME, username);
            }
            if (password != null && !password.isBlank()) {
                properties.setProperty(PropertyKeyConst.PASSWORD, password);
            }
            created = AiFactory.createAiService(properties);
            prompts = new NacosPromptListener(created);
        } catch (Exception error) {
            log.warning("prompt listener was not started");
            close(created);
            created = null;
        }
        this.ai = created;
        this.listener = prompts;
    }

    /**
     * 返回未交给模型的模板。没有这条键、正文为空白，或监听器没建起来时返回 null。
     * 不把调用方的 fallback 传进监听器，避免它先渲染兜底、绕过占位符检查。
     */
    @Override
    public String load(String key) {
        if (listener == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            String template = listener.getPrompt(key);
            if (template == null || template.isBlank()) {
                return null;
            }
            return template;
        } catch (Exception error) {
            throw new IllegalStateException("prompt listener failed");
        }
    }

    /** 进程退出时关掉客户端。没建起来就什么都不做。 */
    @Override
    public void destroy() {
        close(ai);
    }

    /** 关闭失败只记一笔，不掩盖已经完成的回复。 */
    private static void close(AiService ai) {
        if (ai == null) {
            return;
        }
        try {
            ai.shutdown();
        } catch (Exception error) {
            log.warning("prompt listener shutdown failed");
        }
    }
}
