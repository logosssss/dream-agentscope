package com.zhu.product.docs;

import com.zhu.docs.api.DocsAgent;
import com.zhu.docs.api.DocsAskPrompt;
import com.zhu.product.api.ProductModule;
import com.zhu.product.docs.api.DocsController;
import com.zhu.runtime.application.turn.ResolvedChatModel;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 文档助手产品。不是 main Agent，只回答一篇已经注入的文档。
 * 工具、子代理和会话持久化都关掉。记忆配置没有关闭开关，公共项仍会装上节流记忆，
 * 所以这里关掉记忆工具和会话持久化，提问侧每次使用新的会话标识。
 */
@Component
public class DocsProductModule implements ProductModule {

    private final ResolvedChatModel chatModel;

    /** 模型名与对话共用解析结果，不在这里写死供应商。 */
    public DocsProductModule(ResolvedChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 固定标识。通用对话点这个名字会被拒绝。 */
    @Override
    public String agentId() {
        return DocsAgent.ID;
    }

    /** 页面和默认对话仍走搭子。 */
    @Override
    public boolean mainAgent() {
        return false;
    }

    /** 不挂目录工具，避免模型去读仓库或调用业务工具。 */
    @Override
    public List<ToolDescriptor> tools() {
        return List.of();
    }

    /** 一轮结束。文档问答不需要工具循环。 */
    @Override
    public CompletionCriteria completion() {
        return new CompletionCriteria(1);
    }

    /**
     * 任何键都返回同一份原文。空提示词会让模型用通用知识回答，所以这里不看键。
     *
     * @param key 提示词键，忽略
     */
    @Override
    public String promptFallback(String key) {
        return DocsAskPrompt.SYSTEM;
    }

    /**
     * 公共项已经关掉 shell、动态技能和压缩。这里再关掉网页、文件、记忆工具和会话持久化。
     * 轮数压到 1，系统提示用原文。
     */
    @Override
    public void customize(HarnessAgent.Builder builder) {
        builder.disableWebTools();
        builder.disableFilesystemTools();
        builder.disableMemoryTools();
        builder.disableMemoryHooks();
        builder.disableSessionPersistence();
        builder.disableSubagents();
        builder.name("docs")
                .agentId(DocsAgent.ID)
                .model(chatModel.resolve(null))
                .sysPrompt(DocsAskPrompt.SYSTEM)
                .maxIters(1);
    }

    /** 文档站六个 URL 都在这一个控制器上。调度和适配器不是 HTTP 入口。 */
    @Override
    public List<Class<?>> controllers() {
        return List.of(DocsController.class);
    }
}
