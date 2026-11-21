**<font style="color:rgb(15, 17, 21);">基线约束</font>**<font style="color:rgb(15, 17, 21);">：对接 AgentScope 2.0.4 后端（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>`<font style="color:rgb(15, 17, 21);"> 模块），统一走 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">/api/chat</font>`<font style="color:rgb(15, 17, 21);"> 与 SSE 流式接口，支持多租户隔离与 Agent 动态切换。</font>  
**<font style="color:rgb(15, 17, 21);">核心原则</font>**<font style="color:rgb(15, 17, 21);">：微内核架构、Agent 插件化注册、UI/逻辑强解耦、设计系统统一。</font>

## <font style="color:rgb(15, 17, 21);">一、 技术栈选型</font>
<font style="color:rgb(15, 17, 21);">多 Agent 平台前端面临的核心挑战是</font>**<font style="color:rgb(15, 17, 21);">“通用性”与“个性化”的平衡</font>**<font style="color:rgb(15, 17, 21);">，技术选型必须支撑高度组件化和插槽扩展能力。</font>

| <font style="color:rgb(15, 17, 21);">层级</font> | <font style="color:rgb(15, 17, 21);">技术选择</font> | <font style="color:rgb(15, 17, 21);">版本基线</font> | <font style="color:rgb(15, 17, 21);">选型依据</font> |
| --- | --- | --- | --- |
| **<font style="color:rgb(15, 17, 21);">核心框架</font>** | **<font style="color:rgb(15, 17, 21);">Vue 3</font>**<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);"><script setup></font>` | <font style="color:rgb(15, 17, 21);">3.5+</font> | <font style="color:rgb(15, 17, 21);">Composition API 极适合抽象通用逻辑（如流式处理）</font> |
| **<font style="color:rgb(15, 17, 21);">构建工具</font>** | **<font style="color:rgb(15, 17, 21);">Vite 6</font>** | <font style="color:rgb(15, 17, 21);">6.0+</font> | <font style="color:rgb(15, 17, 21);">极速冷启动与 HMR，原生支持 ESM 按需加载</font> |
| **<font style="color:rgb(15, 17, 21);">开发语言</font>** | **<font style="color:rgb(15, 17, 21);">TypeScript</font>** | <font style="color:rgb(15, 17, 21);">5.6+</font> | <font style="color:rgb(15, 17, 21);">与后端 DTO 建立强类型契约，减少联调 Bug</font> |
| **<font style="color:rgb(15, 17, 21);">状态管理</font>** | **<font style="color:rgb(15, 17, 21);">Pinia</font>** | <font style="color:rgb(15, 17, 21);">2.2+</font> | <font style="color:rgb(15, 17, 21);">模块化设计完美契合多 Agent 的 Store 隔离</font> |
| **<font style="color:rgb(15, 17, 21);">路由</font>** | **<font style="color:rgb(15, 17, 21);">Vue Router 4</font>** | <font style="color:rgb(15, 17, 21);">4.4+</font> | <font style="color:rgb(15, 17, 21);">动态路由（根据 Agent 注册表生成）</font> |
| **<font style="color:rgb(15, 17, 21);">样式方案</font>** | **<font style="color:rgb(15, 17, 21);">Tailwind CSS v4</font>** | <font style="color:rgb(15, 17, 21);">4.x</font> | <font style="color:rgb(15, 17, 21);">CSS-first 配置，天然支持基于 CSS 变量的动态主题覆盖</font> |
| **<font style="color:rgb(15, 17, 21);">UI 组件库</font>** | **<font style="color:rgb(15, 17, 21);">Radix Vue</font>**<font style="color:rgb(15, 17, 21);"> (或 shadcn-vue)</font> | <font style="color:rgb(15, 17, 21);">最新</font> | <font style="color:rgb(15, 17, 21);">Headless 架构，无预设样式，方便不同 Agent 定制 UI</font> |
| **<font style="color:rgb(15, 17, 21);">流式解析</font>** | **<font style="color:rgb(15, 17, 21);">Fetch + ReadableStream</font>** | <font style="color:rgb(15, 17, 21);">原生 API</font> | <font style="color:rgb(15, 17, 21);">支持自定义 Header，替代无法发 POST 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">EventSource</font>` |
| **<font style="color:rgb(15, 17, 21);">Markdown</font>** | **<font style="color:rgb(15, 17, 21);">markdown-it</font>**<font style="color:rgb(15, 17, 21);"> + 自定义流式插件</font> | <font style="color:rgb(15, 17, 21);">最新</font> | <font style="color:rgb(15, 17, 21);">解决流式输出时代码块/列表渲染抖动的问题</font> |
| **<font style="color:rgb(15, 17, 21);">工程规范</font>** | **<font style="color:rgb(15, 17, 21);">ESLint + Prettier + Husky</font>** | <font style="color:rgb(15, 17, 21);">最新</font> | <font style="color:rgb(15, 17, 21);">保证多人协作代码一致性</font> |


## <font style="color:rgb(15, 17, 21);">二、 架构设计与工程分层</font>
<font style="color:rgb(15, 17, 21);">采用</font>**<font style="color:rgb(15, 17, 21);">微内核（Micro-kernel）+ 插件（Plugin）</font>**<font style="color:rgb(15, 17, 21);"> 架构。前端工程被划分为三层：平台层、核心层、Agent 插件层。</font>

### <font style="color:rgb(15, 17, 21);">2.1 架构依赖方向</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
[ app 入口 ]
    │
    ├── 依赖 -> [ platform 平台层 ]
    │               │
    │               ├── 依赖 -> [ chat-core 通用对话内核 ] (平台核心)
    │               │
    │               └── 依赖 -> [ agents 插件层 ] (通过路由动态加载)
    │                               │
    │                               └── 依赖 -> [ chat-core ] (通过插槽注入)
```

**<font style="color:rgb(15, 17, 21);">铁律</font>**<font style="color:rgb(15, 17, 21);">：</font>

1. `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">platform/chat-core</font>`<font style="color:rgb(15, 17, 21);"> 绝对不允许引入任何 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/</font>`<font style="color:rgb(15, 17, 21);"> 下的代码。</font>
2. `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/A</font>`<font style="color:rgb(15, 17, 21);"> 不允许直接引入 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/B</font>`<font style="color:rgb(15, 17, 21);">。跨 Agent 共享的组件必须下沉到 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">platform/shared</font>`<font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">2.2 目录结构</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
src/
├── app/                          # 应用入口
│   ├── main.ts
│   ├── router/                   # 路由配置（动态扫描生成）
│   └── styles/                   # 全局 Token、基础 CSS Reset
│
├── platform/                     # 平台层
│   ├── layout/                   # 整体骨架（侧边栏、Header）
│   │   ├── AppLayout.vue
│   │   └── AgentSwitcher.vue     # 左侧 Agent 切换导航
│   │
│   ├── chat-core/                # 🔥 核心：通用对话内核
│   │   ├── components/
│   │   │   ├── ChatContainer.vue # 通用聊天布局（含各种 Slots）
│   │   │   ├── MessageBubble.vue # 通用消息气泡
│   │   │   └── ChatInput.vue     # 通用输入框
│   │   ├── composables/
│   │   │   ├── useChatEngine.ts  # 对话引擎（自动处理 agentId 路由）
│   │   │   └── useStreaming.ts   # SSE 流式解析底座
│   │   ├── api/
│   │   │   └── chat.api.ts
│   │   └── stores/
│   │       └── useChatStore.ts   # 多 Agent 会话状态中心
│   │
│   ├── registry/                 # 🔥 插件注册中心
│   │   ├── index.ts              # 收集所有 Agent 插件
│   │   └── types.ts              # AgentRegistration 契约定义
│   │
│   └── shared/                   # 平台级共享资源
│       ├── api/client.ts         # Axios 拦截器（Token、租户）
│       ├── components/ui/        # 基础原子组件（Button, Modal等）
│       └── utils/                # 通用工具函数
│
├── agents/                       # 🔥 业务插件层
│   ├── companion/                # 智能搭子（第一个插件）
│   │   ├── index.ts              # 插件注册声明
│   │   ├── views/
│   │   │   └── CompanionView.vue # 仅组装 ChatContainer + 专属插槽
│   │   ├── components/           # 搭子专属业务组件
│   │   └── stores/               # 搭子专属状态（如情绪记录）
│   │
│   └── writer/                   # 公文写作助手（未来插件）
│       ├── index.ts
│       └── views/WriterView.vue
│
└── assets/                       # 静态图片、字体
```

## <font style="color:rgb(15, 17, 21);">三、 核心机制设计</font>
### <font style="color:rgb(15, 17, 21);">3.1 Agent 插件注册中心 (Registry)</font>
<font style="color:rgb(15, 17, 21);">新增一个 Agent 时，前端只需要在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/</font>`<font style="color:rgb(15, 17, 21);"> 下新建一个目录，并导出一个注册对象。</font>

**<font style="color:rgb(15, 17, 21);">契约定义 (</font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">platform/registry/types.ts</font>**`**<font style="color:rgb(15, 17, 21);">)</font>**

<font style="color:rgb(15, 17, 21);">typescript</font>

```plain
import { Component } from 'vue';

export interface AgentRegistration {
  id: string;               // 与后端对应的 agentId (如: companion-v1)
  slug: string;             // 前端路由 (如: 'companion')
  name: string;             // 导航栏显示名 (如: '智能搭子')
  icon: string;             // 侧边栏图标标识
  themeColor: string;       // 进入该 Agent 时，平台注入的主题色
  component: () => Promise<Component>; // 异步加载主视图
}
```

**<font style="color:rgb(15, 17, 21);">插件实现 (</font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/companion/index.ts</font>**`**<font style="color:rgb(15, 17, 21);">)</font>**

<font style="color:rgb(15, 17, 21);">typescript</font>

```plain
export const companionAgent: AgentRegistration = {
  id: 'companion-v1',
  slug: 'companion',
  name: '智能搭子',
  icon: 'Heart',
  themeColor: '#ec4899', // 粉色主题
  component: () => import('./views/CompanionView.vue')
};
```

**<font style="color:rgb(15, 17, 21);">路由自动生成 (</font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">app/router/index.ts</font>**`**<font style="color:rgb(15, 17, 21);">)</font>**

<font style="color:rgb(15, 17, 21);">typescript</font>

```plain
import { agentList } from '@/platform/registry';

const routes = agentList.map(agent => ({
  path: `/${agent.slug}`,
  name: agent.name,
  component: agent.component,
  meta: { themeColor: agent.themeColor, agentId: agent.id }
}));
```

### <font style="color:rgb(15, 17, 21);">3.2 通用对话内核 (Chat Core) 的插槽架构</font>
<font style="color:rgb(15, 17, 21);">为了兼容所有 Agent 形态，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ChatContainer.vue</font>`<font style="color:rgb(15, 17, 21);"> 必须暴露丰富的插槽，让具体 Agent 决定“长什么样”，而核心只负责“怎么跑”。</font>

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ChatContainer.vue</font>**`**<font style="color:rgb(15, 17, 21);"> 插槽设计：</font>**

<font style="color:rgb(15, 17, 21);">vue</font>

```plain
<template>
  <div class="chat-layout">
    <!-- 左插槽：允许 Agent 注入专属面板（如搭子情绪面板、写作目录树） -->
    <slot name="sidebar" />

    <div class="main-area">
      <MessageList>
        <!-- 消息气泡插槽：允许 Agent 覆盖特定格式的渲染 -->
        <template #bubble="{ message }">
          <slot name="custom-bubble" :message="message">
            <!-- 默认气泡渲染 -->
            <MessageBubble :message="message" />
          </slot>
        </template>
      </MessageList>

      <!-- 输入框上方插槽：如安全警告、快捷提示语 -->
      <slot name="before-input" />

      <ChatInput @send="handleSend">
        <!-- 输入框工具栏：允许 Agent 注入特定能力（如文件上传、公式按钮） -->
        <template #tools>
          <slot name="input-tools" />
        </template>
      </ChatInput>
    </div>
  </div>
</template>
```

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">CompanionView.vue</font>**`**<font style="color:rgb(15, 17, 21);"> (搭子主页面) 的极简实现：</font>**

<font style="color:rgb(15, 17, 21);">vue</font>

```plain
<template>
  <ChatContainer 
    :agent-id="agent.id" 
    :session-id="sessionId"
  >
    <!-- 注入搭子专属情绪侧边栏 -->
    <template #sidebar>
      <MoodTracker :user-id="userId" />
    </template>

    <!-- 注入命中安全门时的危机提醒 -->
    <template #before-input>
      <CrisisAlert v-if="isCrisisTriggered" />
    </template>
  </ChatContainer>
</template>
```

### <font style="color:rgb(15, 17, 21);">3.3 状态管理：多 Agent 隔离设计</font>
<font style="color:rgb(15, 17, 21);">所有 Agent 共享一个 Pinia Store，但通过复合 Key 进行数据隔离。</font>

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">platform/chat-core/stores/useChatStore.ts</font>**`

<font style="color:rgb(15, 17, 21);">typescript</font>

```plain
export const useChatStore = defineStore('chat', () => {
  // 核心：使用 `${agentId}:${sessionId}` 作为隔离键
  const sessions = ref<Record<string, ChatMessage[]>>({});

  function getMessages(agentId: string, sessionId: string) {
    const key = `${agentId}:${sessionId}`;
    if (!sessions.value[key]) {
      sessions.value[key] = [];
    }
    return sessions.value[key];
  }

  // 当用户从“搭子”切换到“写作助手”时，自动切换到对应的 messages
  return { getMessages };
});
```

### <font style="color:rgb(15, 17, 21);">3.4 SSE 流式解析引擎（支持多 Agent）</font>
`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">platform/chat-core/composables/useStreaming.ts</font>**`

<font style="color:rgb(15, 17, 21);">typescript</font>

```plain
export function useStreaming() {
  async function startStream(agentId: string, payload: ChatRequest, onChunk: (text: string) => void) {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Caller-Token': getToken(),
        'X-Tenant-Id': getTenantId(),
        'X-User-Id': getUserId(),
      },
      // 关键：动态传递 agentId 给后端
      body: JSON.stringify({ ...payload, agentId }),
    });

    // ... ReadableStream 消费逻辑（略）...
    // 遇到 "data: {\"type\":\"text\",\"text\":\"...\"}" 时，调用 onChunk
  }
  return { startStream };
}
```

## <font style="color:rgb(15, 17, 21);">四、 设计系统与样式规范</font>
<font style="color:rgb(15, 17, 21);">由于有多个不同风格的 Agent，平台层必须统一基础 UI 规范（圆角、阴影、间距），而业务层通过 </font>**<font style="color:rgb(15, 17, 21);">CSS 变量</font>**<font style="color:rgb(15, 17, 21);"> 实现 Agent 专属主题注入。</font>

### <font style="color:rgb(15, 17, 21);">4.1 三层 Design Token 架构</font>
`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">app/styles/main.css</font>**`

<font style="color:rgb(15, 17, 21);">css</font>

```plain
@import "tailwindcss";

@theme {
  /* === Layer 1: 基础色板 (不可直接用于组件) === */
  --color-blue-500: #3b82f6;
  --color-pink-500: #ec4899;
  --color-gray-50: #f9fafb;
  --color-gray-900: #111827;

  /* === Layer 2: 语义 Token (组件只用这一层) === */
  --color-brand-primary: var(--color-blue-500); /* 默认品牌色 */
  --color-surface-bg: var(--color-gray-50);
  --color-surface-card: #ffffff;
  --color-text-main: var(--color-gray-900);

  /* === Layer 3: 组件级 Token === */
  --chat-bubble-radius: 1rem;
  --chat-bubble-bg-user: var(--color-brand-primary);
  --chat-bubble-bg-assistant: var(--color-surface-card);
}

/* 暗色模式覆盖 */
[data-theme="dark"] {
  --color-surface-bg: #0f172a;
  --color-surface-card: #1e293b;
  --color-text-main: #f1f5f9;
}
```

### <font style="color:rgb(15, 17, 21);">4.2 Agent 专属主题动态注入</font>
<font style="color:rgb(15, 17, 21);">当路由进入 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">/companion</font>`<font style="color:rgb(15, 17, 21);"> 时，前端读取 Registry 中的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">themeColor</font>`<font style="color:rgb(15, 17, 21);">（粉色），并覆盖平台的默认品牌色。</font>

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">app/router/index.ts</font>**`**<font style="color:rgb(15, 17, 21);"> (路由守卫中实现)</font>**

<font style="color:rgb(15, 17, 21);">typescript</font>

```plain
router.beforeEach((to, from, next) => {
  const themeColor = to.meta.themeColor as string;
  if (themeColor) {
    // 动态覆盖 Layer 2 的语义 Token
    document.documentElement.style.setProperty('--color-brand-primary', themeColor);
  } else {
    // 恢复默认
    document.documentElement.style.removeProperty('--color-brand-primary');
  }
  next();
});
```

<font style="color:rgb(15, 17, 21);">这样，搭子聊天时整体 UI 呈现温暖的粉色系，切换到写作助手时呈现专业的蓝色系，</font>**<font style="color:rgb(15, 17, 21);">代码中无需任何 if-else，全靠 CSS 变量穿透</font>**<font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">4.3 排版与间距规范</font>
+ **<font style="color:rgb(15, 17, 21);">字号</font>**<font style="color:rgb(15, 17, 21);">：正文 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">text-base</font>`<font style="color:rgb(15, 17, 21);"> (16px)，辅助信息 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">text-sm</font>`<font style="color:rgb(15, 17, 21);"> (14px)。流式输出期间的 Markdown 必须统一行高 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">leading-relaxed</font>`<font style="color:rgb(15, 17, 21);">。</font>
+ **<font style="color:rgb(15, 17, 21);">间距</font>**<font style="color:rgb(15, 17, 21);">：卡片内边距 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">p-4</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">p-6</font>`<font style="color:rgb(15, 17, 21);">，消息气泡间距 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">gap-4</font>`<font style="color:rgb(15, 17, 21);">。</font>
+ **<font style="color:rgb(15, 17, 21);">无障碍</font>**<font style="color:rgb(15, 17, 21);">：全局组件必须包含 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">focus-visible:ring-2</font>`<font style="color:rgb(15, 17, 21);"> 焦点环，以支持纯键盘导航。</font>

---

## <font style="color:rgb(15, 17, 21);">五、 工程规范与质量保障</font>
### <font style="color:rgb(15, 17, 21);">5.1 文件职责边界</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">views/*.vue</font>`<font style="color:rgb(15, 17, 21);">：页面级组装，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);"><script setup></font>`**<font style="color:rgb(15, 17, 21);">不得超过 50 行</font>**<font style="color:rgb(15, 17, 21);">。只负责组合 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ChatContainer</font>`<font style="color:rgb(15, 17, 21);"> 和插槽。</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">components/*.vue</font>`<font style="color:rgb(15, 17, 21);">：业务 UI 组件，只接受 props，抛出 emits，禁止在其中直接调用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">fetch</font>`<font style="color:rgb(15, 17, 21);">。</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">composables/*.ts</font>`<font style="color:rgb(15, 17, 21);">：数据逻辑抽离，必须可以通过 Vitest 进行纯逻辑测试，不依赖 Vue 实例。</font>

### <font style="color:rgb(15, 17, 21);">5.2 命名规范</font>
+ <font style="color:rgb(15, 17, 21);">组件文件：PascalCase（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MessageBubble.vue</font>`<font style="color:rgb(15, 17, 21);">）。</font>
+ <font style="color:rgb(15, 17, 21);">Composable：以 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">use</font>`<font style="color:rgb(15, 17, 21);"> 开头（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">useChatEngine.ts</font>`<font style="color:rgb(15, 17, 21);">）。</font>
+ <font style="color:rgb(15, 17, 21);">类型定义：以 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">.types.ts</font>`<font style="color:rgb(15, 17, 21);"> 结尾（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">chat.types.ts</font>`<font style="color:rgb(15, 17, 21);">）。</font>
+ <font style="color:rgb(15, 17, 21);">插件注册：统一为 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">index.ts</font>`<font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">5.3 提交与 CI 检查</font>
+ **<font style="color:rgb(15, 17, 21);">Husky 钩子</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">pre-commit</font>`<font style="color:rgb(15, 17, 21);"> 强制运行 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">eslint --fix</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">vue-tsc --noEmit</font>`<font style="color:rgb(15, 17, 21);">，类型报错绝对不允许提交。</font>
+ **<font style="color:rgb(15, 17, 21);">测试</font>**<font style="color:rgb(15, 17, 21);">：使用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Vitest</font>`<font style="color:rgb(15, 17, 21);"> 覆盖 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">useStreaming</font>`<font style="color:rgb(15, 17, 21);"> 的 SSE 拆包逻辑和 Markdown 解析器，覆盖率不低于 70%。</font>

---

## <font style="color:rgb(15, 17, 21);">六、 联调与接口契约对齐</font>
### <font style="color:rgb(15, 17, 21);">6.1 关键请求头约定</font>
<font style="color:rgb(15, 17, 21);">由于后端 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>`<font style="color:rgb(15, 17, 21);"> 设计了 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AccessIdentityFilter</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">CallerTokenFilter</font>`<font style="color:rgb(15, 17, 21);">，前端请求必须全部携带：</font>

| <font style="color:rgb(15, 17, 21);">Header</font> | <font style="color:rgb(15, 17, 21);">来源</font> | <font style="color:rgb(15, 17, 21);">用途</font> |
| --- | --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">X-Caller-Token</font>` | <font style="color:rgb(15, 17, 21);">本地环境变量或登录返回</font> | <font style="color:rgb(15, 17, 21);">鉴权（后端验证这是合法客户端）</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">X-Tenant-Id</font>` | <font style="color:rgb(15, 17, 21);">登录态或选择租户</font> | <font style="color:rgb(15, 17, 21);">多租户隔离</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">X-User-Id</font>` | <font style="color:rgb(15, 17, 21);">登录态</font> | <font style="color:rgb(15, 17, 21);">用户隔离</font> |


### <font style="color:rgb(15, 17, 21);">6.2 SSE 接口契约</font>
<font style="color:rgb(15, 17, 21);">后端返回的流式响应必须被前端 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">useStreaming</font>`<font style="color:rgb(15, 17, 21);"> 正确解析：</font>

+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">data: {"type": "text", "text": "你"}</font>`<font style="color:rgb(15, 17, 21);"> -> 追加至文本 buffer。</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">data: {"type": "approval", "data": {...}}</font>`<font style="color:rgb(15, 17, 21);"> -> 在消息流中触发审批卡片插槽。</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">data: [DONE]</font>`<font style="color:rgb(15, 17, 21);"> -> 结束流，解除输入框锁定。</font>

### <font style="color:rgb(15, 17, 21);">6.3 联调注意事项</font>
+ **<font style="color:rgb(15, 17, 21);">人格不可漂移</font>**<font style="color:rgb(15, 17, 21);">：后端明确忽略客户端传递的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">systemPrompt</font>`<font style="color:rgb(15, 17, 21);">。前端不需要在 UI 上给用户提供“修改系统提示词”的入口，避免用户产生困惑（“为什么我改了不生效”）。</font>
+ **<font style="color:rgb(15, 17, 21);">安全门危机话术</font>**<font style="color:rgb(15, 17, 21);">：当流式返回的内容被判定为危机话术时，前端依然正常渲染，不需要特殊拦截，但可以配合 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/companion</font>`<font style="color:rgb(15, 17, 21);"> 的专属样式（如显示一个求助电话入口）。</font>

---

## <font style="color:rgb(15, 17, 21);">七、 实施路线建议（单人开发优先）</font>
<font style="color:rgb(15, 17, 21);">作为单人开发，不要一上来就搞复杂的动态注册和插槽。请按照以下顺序演进：</font>

| <font style="color:rgb(15, 17, 21);">阶段</font> | <font style="color:rgb(15, 17, 21);">核心任务</font> | <font style="color:rgb(15, 17, 21);">验证目标</font> |
| --- | --- | --- |
| **<font style="color:rgb(15, 17, 21);">Step 1： 单点闭环</font>** | <font style="color:rgb(15, 17, 21);">先用最平铺直叙的方式，只写搭子的单页面。跑通 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">useStreaming</font>`<br/><font style="color:rgb(15, 17, 21);">，确保文字能流式打出来。</font> | <font style="color:rgb(15, 17, 21);">页面能聊天，不卡顿。</font> |
| **<font style="color:rgb(15, 17, 21);">Step 2： 抽取内核</font>** | <font style="color:rgb(15, 17, 21);">将搭子页面中的聊天框抽离到 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">platform/chat-core/ChatContainer.vue</font>`<br/><font style="color:rgb(15, 17, 21);">。搭子页面改为调用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);"><ChatContainer /></font>`<br/><font style="color:rgb(15, 17, 21);">。</font> | <font style="color:rgb(15, 17, 21);">拆分后，搭子功能完全不受影响。</font> |
| **<font style="color:rgb(15, 17, 21);">Step 3： 建立注册表</font>** | <font style="color:rgb(15, 17, 21);">建立 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">registry</font>`<br/><font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AppLayout</font>`<br/><font style="color:rgb(15, 17, 21);">。把搭子页面移动到 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agents/companion</font>`<br/><font style="color:rgb(15, 17, 21);">。</font> | <font style="color:rgb(15, 17, 21);">侧边栏出现“智能搭子”图标，点击进入聊天页。</font> |
| **<font style="color:rgb(15, 17, 21);">Step 4： 验证插件化</font>** | <font style="color:rgb(15, 17, 21);">随便写一个 10 行的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentBView.vue</font>`<br/><font style="color:rgb(15, 17, 21);">，挂载一个默认的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);"><ChatContainer /></font>`<br/><font style="color:rgb(15, 17, 21);">，在注册表加一行。</font> | <font style="color:rgb(15, 17, 21);">侧边栏出现第二个图标，点击能切换，各自独立聊天。</font> |
| **<font style="color:rgb(15, 17, 21);">Step 5： 个性化定制</font>** | <font style="color:rgb(15, 17, 21);">使用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ChatContainer</font>`<br/><font style="color:rgb(15, 17, 21);"> 的插槽，给搭子加上专属的侧边栏，并在路由守卫中应用粉色主题。</font> | <font style="color:rgb(15, 17, 21);">搭子页面看起来独一无二。</font> |
| **<font style="color:rgb(15, 17, 21);">Step 6： 完善基础体验</font>** | <font style="color:rgb(15, 17, 21);">加 Markdown 流式渲染、自动滚动、暗黑模式、停止按钮。</font> | <font style="color:rgb(15, 17, 21);">体验达到生产级。</font> |


**<font style="color:rgb(15, 17, 21);">一句话总结</font>**<font style="color:rgb(15, 17, 21);">：用“注册表驱动路由、复合 Key 驱动状态、CSS 变量驱动主题、插槽驱动形态差异”的微内核架构，能让你的前端在多 Agent 时代无需重构，只需不断添加插件。</font>



