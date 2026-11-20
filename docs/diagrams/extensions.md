# 扩展面图（14～18）

须标「默认关」或「学习面」。结论权威：各 `docs/*-落地.md`。索引见 [README.md](./README.md)。  
不宣称产品 Remote 工人、`mode=mq`、MCP 服务发现已上线。

## 14. MCP 装配（空清单不连）

清单来自业务配置前缀 `agentscope.runtime.mcp.servers`；仅 companion；不做 MCP 服务发现；密钥不进 Nacos 明文。

```mermaid
flowchart TB
  Cfg["agentscope.runtime.mcp.servers"]
  Empty{"清单为空？"}
  Skip["不设 toolsConfig"]
  Fac["McpToolsConfigFactory\n${ENV} 展开"]
  Comp["customizeCompanion\nToolsConfig.mcpServers"]
  Req["required=true → 硬失败\n否则软失败跳过该 server"]

  Cfg --> Empty
  Empty -->|是| Skip
  Empty -->|否| Fac --> Comp --> Req
```

## 15. Sandbox（默认关）

默认 Remote USER 工作区。`sandbox.enabled=true` 时仅 companion 换 Docker；缺 Store 或 `docker version` **硬失败**，不回退 Remote；echo 仍 Remote；shell 仍关。

```mermaid
flowchart TB
  En{"sandbox.enabled"}
  Remote["RemoteFilesystemSpec\nIsolationScope.USER"]
  Docker["DockerFilesystemSpec\n推荐 ubuntu:24.04"]
  Probe["DockerCliProbe"]
  Store{"DistributedStore 可用？"}
  Fail["启动/重建硬失败"]

  En -->|false / 默认| Remote
  En -->|true companion| Store
  Store -->|否| Fail
  Store -->|是| Probe
  Probe -->|失败| Fail
  Probe -->|成功| Docker
```

## 16. Wakeup（默认关）

进程内 Bus；`POST /api/internal/wakeups` + `X-Wakeup-Token`（`AGENTSCOPE_WAKEUP_TOKEN` 仅环境变量）；仅 companion；忙会话跳过。不做调度表消费 / Inbox / TeamWakeups。

```mermaid
sequenceDiagram
  participant Ext as 外部调用方
  participant F as WakeupTokenFilter
  participant Port as WakeupPort
  participant Bus as InMemoryMessageBus
  participant Coord as WakeupCoordinator
  participant Gw as ChannelGateway

  Ext->>F: POST /api/internal/wakeups\n+ X-Wakeup-Token
  F->>Port: 令牌通过
  Port->>Bus: enqueue
  Bus->>Coord: dispatch
  Coord->>Gw: 懒解析 Target / 投递
  Note over Coord: enabled=false 时不启 Bus；忙会话跳过
```

## 17. Skills / Curator（默认关 manage/Curator）

工厂 `disableDynamicSkills`；工作区默认 skill 仍开、目录可空、不写种子。不调 `enableSkillManageTool` / `enableSkillCurator`。Marketplace / Promotion / 产品开启另块。

```mermaid
flowchart TB
  Common["configureCommon\ndisableDynamicSkills"]
  Ws["工作区默认 skill 可空"]
  Load["load_skill 面保留"]
  Manage["enableSkillManageTool\n默认不调用"]
  Curator["enableSkillCurator\n单独开无效；嵌在 manage"]

  Common --> Ws --> Load
  Manage -.->|本仓不开| X1["产品开启另块"]
  Curator -.->|本仓不开| X1
```

## 18. Subagent（本地 probe；Remote 学习面）

companion 挂静态 `subagent-probe`（ISOLATED）；动态子代理关闭。Remote：声明面 spike 已钉，**工厂不挂**、非产品远程工人。

```mermaid
flowchart TB
  Dyn["disableDynamicSubagents"]
  Probe["静态 subagent-probe\nISOLATED / 继承权限 / 零工具"]
  RemoteDecl["SubagentDeclaration.url(...)\nisRemote / AskPolicy"]
  Spike["RemoteSubagentSurfaceSpikeTest\n学习面：不发 HTTP"]
  Factory["HarnessAgentFactory\n不挂 Remote 声明"]

  Dyn --> Probe
  RemoteDecl --> Spike
  Spike -.->|产品工人另 grill| Factory
  Probe --> Factory
```
