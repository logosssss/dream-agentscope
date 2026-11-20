package com.zhu.runtime.api.agent;

import reactor.core.publisher.Flux;

/**
 * 运行层对外端口：对话执行缝。正式实现是 ChannelGateway（长驻 Agent）；控制器与 ChatAppService 只依赖本接口。
 * WEB 走 ChatUi；API 走同进程注册的 ApiChannel（见 docs/api-channel-落地.md）。
 *
 * <p>错误与生命周期契约（行为已在实现中，此处仅钉文档）：
 * <ul>
 *   <li>同步：内部 {@code block} + 运行超时；超时以运行时异常向上抛，由接入层映射。</li>
 *   <li>流式：Flux 带 timeout；订阅取消 / 完成 / 错误须放行排水闸（实现用 doFinally / 同步 catch）。</li>
 *   <li>ASK / RequireUserConfirm：不经本接口返回审批决策；流式映射为事件，续跑走
 *       {@code PendingAskFacade} 另缝，不在此端口做确认。</li>
 * </ul>
 */
public interface AgentExecutor {

    /**
     * WEB 同步执行一轮（ChatUi）。同 session 排队由 Gateway 负责。
     * 超时抛错；成功返回文本与 runId。
     */
    AgentResponse execute(AgentRequest request);

    /**
     * API Channel 同步执行一轮。护栏与 {@link #execute} 相同，通道不同。
     */
    AgentResponse executeApi(AgentRequest request);

    /**
     * WEB 流式执行一轮（ChatUi）。调用方不要 block。
     * 超时由 Flux timeout 信号表达；取消/完成/失败时实现须放行排水闸。
     * 待确认事件会出现在流中，决策与续跑不在本方法返回值里完成。
     */
    Flux<AgentStreamEvent> stream(AgentRequest request);

    /**
     * API Channel 流式执行一轮。契约同 {@link #stream}。
     */
    Flux<AgentStreamEvent> streamApi(AgentRequest request);
}
