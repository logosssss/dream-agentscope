**<font style="color:rgb(15, 17, 21);">基线架构</font>**<font style="color:rgb(15, 17, 21);">：7 模块底座 + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 模块</font>  
**<font style="color:rgb(15, 17, 21);">核心定位</font>**<font style="color:rgb(15, 17, 21);">：Agentic RAG——RAG 不是前置拦截器，而是 Agent 可自主调用的工具</font>  
**<font style="color:rgb(15, 17, 21);">演进路线</font>**<font style="color:rgb(15, 17, 21);">：Naive → Advanced → 评估体系 → GraphRAG，四阶段递进，每一阶段独立可上线</font>

## <font style="color:rgb(15, 17, 21);">一、总体架构落位</font>
### <font style="color:rgb(15, 17, 21);">1.1 模块职责划分</font>
| <font style="color:rgb(15, 17, 21);">模块</font> | <font style="color:rgb(15, 17, 21);">RAG 职责</font> | <font style="color:rgb(15, 17, 21);">明确不做</font> |
| --- | --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>` | <font style="color:rgb(15, 17, 21);">向量写入、近邻检索、删除；</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">knowledge</font>`<br/><font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">episodic_memory</font>`<br/><font style="color:rgb(15, 17, 21);"> 两个 Collection 的管理</font> | <font style="color:rgb(15, 17, 21);">不做 Embedding、不做切片、不做 RRF、不做 Rerank</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<br/><font style="color:rgb(15, 17, 21);">（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ProductTool</font>`<br/><font style="color:rgb(15, 17, 21);"> 实现）；</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GraphSearchTool</font>`<br/><font style="color:rgb(15, 17, 21);">（阶段四）；Embedding 服务调用</font> | <font style="color:rgb(15, 17, 21);">不直接操作 Milvus 客户端，通过 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<br/><font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">VectorStore</font>`<br/><font style="color:rgb(15, 17, 21);"> 接口</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>` | <font style="color:rgb(15, 17, 21);">文档索引异步任务（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DocumentIndexTaskHandler</font>`<br/><font style="color:rgb(15, 17, 21);">）；</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MemoryExtractionMiddleware</font>`<br/><font style="color:rgb(15, 17, 21);"> 中的记忆向量化</font> | <font style="color:rgb(15, 17, 21);">不提供 HTTP 接口；不承担 RAG 工具的业务逻辑</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-state</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_document</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_chunk</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rag_eval_case</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rag_eval_run</font>`<br/><font style="color:rgb(15, 17, 21);"> 等业务表</font> | <font style="color:rgb(15, 17, 21);">不存向量；不调 Milvus</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-audit</font>` | <font style="color:rgb(15, 17, 21);">RAG 评估结果的存储（复用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">eval_run</font>`<br/><font style="color:rgb(15, 17, 21);"> 表）；检索轨迹的审计记录</font> | <font style="color:rgb(15, 17, 21);">不承担知识库管理</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>` | <font style="color:rgb(15, 17, 21);">知识库上传、文档管理、检索配置的 HTTP 入口</font> | <font style="color:rgb(15, 17, 21);">不执行检索逻辑</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-admin</font>` | <font style="color:rgb(15, 17, 21);">评估面板的查询接口；知识库配置的只读视图</font> | <font style="color:rgb(15, 17, 21);">不建 Milvus 集合；不改核心检索参数</font> |


### <font style="color:rgb(15, 17, 21);">1.2 依赖方向</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
agent-semantic
  ├──> agent-vector  (api：VectorStore、KnowledgeFilter)
  └──> agent-common

agent-runtime
  ├──> agent-semantic (contract：ProductTool)
  ├──> agent-vector   (api：VectorStore)
  └──> agent-state    (api：KnowledgeRepository)

agent-access
  ├──> agent-semantic (api：KnowledgeBaseService)
  └──> agent-state    (api：DocumentRepository)
```

**<font style="color:rgb(15, 17, 21);">关键约束</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`**<font style="color:rgb(15, 17, 21);">不依赖</font>**<font style="color:rgb(15, 17, 21);">任何业务模块。它只认 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">VectorStore</font>`<font style="color:rgb(15, 17, 21);"> 接口和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeFilter</font>`<font style="color:rgb(15, 17, 21);">/</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MemoryFilter</font>`<font style="color:rgb(15, 17, 21);">，不知道什么是“文档”、什么是“RAG”。</font>

## <font style="color:rgb(15, 17, 21);">二、四阶段演进路线</font>
### <font style="color:rgb(15, 17, 21);">阶段一：Naive RAG——跑通文档索引与向量检索</font>
**<font style="color:rgb(15, 17, 21);">对应博客内容</font>**<font style="color:rgb(15, 17, 21);">：RAG 核心管线的 Ingest → Index → Retrieve → Generate 四阶段</font><font style="color:rgb(15, 17, 21);">。Naive RAG 是最简单的实现路径：文档分块 → 向量化 → 相似度搜索 → LLM 生成</font><font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">目标</font>**<font style="color:rgb(15, 17, 21);">：实现“上传文档 → Agent 调用检索工具 → 基于检索结果回答”的端到端闭环。</font>

#### <font style="color:rgb(15, 17, 21);">离线索引管线</font>
<font style="color:rgb(15, 17, 21);">文档上传后，通过 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTaskPublisher</font>`<font style="color:rgb(15, 17, 21);"> 发布 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DOCUMENT_INDEX</font>`<font style="color:rgb(15, 17, 21);"> 任务，由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DocumentIndexTaskHandler</font>`<font style="color:rgb(15, 17, 21);"> 异步执行：</font>

1. **<font style="color:rgb(15, 17, 21);">解析</font>**<font style="color:rgb(15, 17, 21);">：根据文件类型（PDF/Word/Markdown）提取纯文本。</font>
2. **<font style="color:rgb(15, 17, 21);">分块</font>**<font style="color:rgb(15, 17, 21);">：按固定长度（如 500 字符）+ 重叠（如 50 字符）切分。分块质量是 Naive RAG 的主要瓶颈，但阶段一不做过多的分块策略优化。</font>
3. **<font style="color:rgb(15, 17, 21);">Embedding</font>**<font style="color:rgb(15, 17, 21);">：调用 Embedding 模型（如 DashScope </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">text-embedding-v2</font>`<font style="color:rgb(15, 17, 21);">，维度 1024）生成向量。</font>
4. **<font style="color:rgb(15, 17, 21);">双写</font>**<font style="color:rgb(15, 17, 21);">：</font>
    - <font style="color:rgb(15, 17, 21);">向量写入 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">knowledge</font>`<font style="color:rgb(15, 17, 21);"> Collection，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">id</font>`<font style="color:rgb(15, 17, 21);"> 使用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_chunk</font>`<font style="color:rgb(15, 17, 21);"> 表的主键。</font>
    - <font style="color:rgb(15, 17, 21);">文本内容和元数据写入 MySQL </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_chunk</font>`<font style="color:rgb(15, 17, 21);"> 表。</font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">knowledge</font>`<font style="color:rgb(15, 17, 21);"> Collection 的字段由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 的 README 固定为 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">id</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">vector</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">user_id</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent_id</font>`<font style="color:rgb(15, 17, 21);">。</font>**<font style="color:rgb(15, 17, 21);">不要改这个 schema</font>**<font style="color:rgb(15, 17, 21);">——它是 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 与上层模块的契约。</font>

#### <font style="color:rgb(15, 17, 21);">在线检索工具</font>
<font style="color:rgb(15, 17, 21);">在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic/infrastructure/tool</font>`<font style="color:rgb(15, 17, 21);"> 实现 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);">：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```plain
@Component
public class KnowledgeSearchTool implements ProductTool {

    private final VectorStore vectorStore;
    private final KnowledgeRepository knowledgeRepo; // 查 kb_chunk

    @Tool(name = "knowledge_search",
          description = "当需要查找专有知识、文档或规范时使用此工具。输入是检索问题。")
    public String search(
        @ToolParam(description = "检索问题") String query,
        RuntimeContext ctx
    ) {
        float[] queryVector = embeddingService.embed(query);
        KnowledgeFilter filter = new KnowledgeFilter(
            ctx.userId(), ctx.agentId(), null  // tenantId 可选
        );
        List<VectorHit> hits = vectorStore.search("knowledge", queryVector, filter, 5);
        if (hits.isEmpty()) return "未找到相关知识。";

        List<KbChunk> chunks = knowledgeRepo.findByIds(hits.stream().map(VectorHit::id).toList());
        return chunks.stream()
            .map(c -> "【来源：" + c.docName() + "】\n" + c.content())
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    public String toolId() { return "knowledge_search"; }
}
```

**<font style="color:rgb(15, 17, 21);">Agent 集成</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">CatalogToolRegistrar</font>`<font style="color:rgb(15, 17, 21);"> 会根据 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">tool_registry</font>`<font style="color:rgb(15, 17, 21);"> 的启用行和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ProductTool</font>`<font style="color:rgb(15, 17, 21);"> 的实现，把这个工具注册进 Agent 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Toolkit</font>`<font style="color:rgb(15, 17, 21);">。Agent 在推理时自主决定是否调用它——这就是 Agentic RAG 的基本形态：检索被设计为一种可供 Agent 调用的工具，而不是在生成前无条件执行的前置步骤。</font>

**<font style="color:rgb(15, 17, 21);">阶段一的已知局限</font>**<font style="color:rgb(15, 17, 21);">：向量检索对关键词缺失敏感——用户搜“订单号 ORD20240512”，向量模型可能完全无法召回含该编号的文档。这就是阶段二要解决的问题。</font>

### <font style="color:rgb(15, 17, 21);">阶段二：Advanced RAG——混合检索、RRF 与重排序</font>
**<font style="color:rgb(15, 17, 21);">对应博客内容</font>**<font style="color:rgb(15, 17, 21);">：Advanced RAG 在 Naive 基础上引入查询改写、混合检索、重排序、上下文压缩</font><font style="color:rgb(15, 17, 21);">。向量检索的三大结构性局限（关键词缺失、短查询歧义、数值符号失真）</font><font style="color:rgb(15, 17, 21);">需要通过 BM25 + RRF + Cross-Encoder 来弥补。</font>

**<font style="color:rgb(15, 17, 21);">目标</font>**<font style="color:rgb(15, 17, 21);">：在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 内部实现双路召回和重排，不改变 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 的接口。</font>

#### <font style="color:rgb(15, 17, 21);">双路召回</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 内部并行执行两路检索：</font>

1. **<font style="color:rgb(15, 17, 21);">向量召回</font>**<font style="color:rgb(15, 17, 21);">：调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">VectorStore.search("knowledge", ...)</font>`<font style="color:rgb(15, 17, 21);">，取 Top 20。</font>
2. **<font style="color:rgb(15, 17, 21);">BM25 召回</font>**<font style="color:rgb(15, 17, 21);">：对 MySQL </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_chunk</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">content</font>`<font style="color:rgb(15, 17, 21);"> 字段执行全文检索（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MATCH(content) AGAINST(? IN BOOLEAN MODE)</font>`<font style="color:rgb(15, 17, 21);">），取 Top 20。</font>

**<font style="color:rgb(15, 17, 21);">前提</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_chunk</font>`<font style="color:rgb(15, 17, 21);"> 表的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">content</font>`<font style="color:rgb(15, 17, 21);"> 字段必须有 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">FULLTEXT</font>`<font style="color:rgb(15, 17, 21);"> 索引。如果 MySQL 全文检索的中文分词效果不佳，可以替换为 Elasticsearch，但阶段二先用 MySQL 全文索引验证链路。</font>

#### <font style="color:rgb(15, 17, 21);">RRF 融合</font>
<font style="color:rgb(15, 17, 21);">在 Java 内存中实现 Reciprocal Rank Fusion。RRF 的核心思想是：不依赖两路检索的原始分数（向量余弦相似度和 BM25 分数不可比），只用排名做融合：</font>

<font style="color:rgb(15, 17, 21);">text</font>

<font style="color:rgb(15, 17, 21);">RRF_score(d) = Σ_{r ∈ 路} 1 / (k + rank_r(d))</font>

<font style="color:rgb(15, 17, 21);">其中 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">k</font>`<font style="color:rgb(15, 17, 21);"> 通常取 60</font><font style="color:rgb(15, 17, 21);">。两路结果合并后按 RRF 分数降序，取 Top 10。</font>

**<font style="color:rgb(15, 17, 21);">关键设计</font>**<font style="color:rgb(15, 17, 21);">：RRF 融合发生在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 内部，</font>**<font style="color:rgb(15, 17, 21);">不修改 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>**`<font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 只负责向量召回，BM25 由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 通过 MySQL 直接完成。</font>

#### <font style="color:rgb(15, 17, 21);">Cross-Encoder 重排序</font>
<font style="color:rgb(15, 17, 21);">对 RRF 融合后的 Top 10，调用重排模型 API（如 DashScope </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">gte-rerank</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bge-reranker-v2-m3</font>`<font style="color:rgb(15, 17, 21);">）做精细打分，取最终 Top 3 注入上下文</font><font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">超时降级</font>**<font style="color:rgb(15, 17, 21);">：Rerank 调用必须设置 2 秒超时。超时后直接使用 RRF 排序结果，</font>**<font style="color:rgb(15, 17, 21);">不阻塞整个 Agent 响应</font>**<font style="color:rgb(15, 17, 21);">。Rerank 是“锦上添花”，不是“必须成功”。</font>

#### <font style="color:rgb(15, 17, 21);">检索模式参数化</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">search</font>`<font style="color:rgb(15, 17, 21);"> 方法增加一个可选参数 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">mode</font>`<font style="color:rgb(15, 17, 21);">：</font>

| <font style="color:rgb(15, 17, 21);">mode</font> | <font style="color:rgb(15, 17, 21);">行为</font> |
| --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">VECTOR</font>` | <font style="color:rgb(15, 17, 21);">只走向量召回（阶段一行为）</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HYBRID</font>` | <font style="color:rgb(15, 17, 21);">向量 + BM25 → RRF → 可选 Rerank</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KEYWORD</font>` | <font style="color:rgb(15, 17, 21);">只走 BM25</font> |


<font style="color:rgb(15, 17, 21);">Agent 可以根据查询类型自主选择模式：精确编号类查询倾向 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KEYWORD</font>`<font style="color:rgb(15, 17, 21);">，语义类查询倾向 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HYBRID</font>`<font style="color:rgb(15, 17, 21);">。默认 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HYBRID</font>`<font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">阶段三：RAG 评估体系——分离检索质量与生成质量</font>
**<font style="color:rgb(15, 17, 21);">对应博客内容</font>**<font style="color:rgb(15, 17, 21);">：RAG 最危险的失败模式不是报错，而是“看起来对”</font><font style="color:rgb(15, 17, 21);">。评估必须拆成检索质量和生成质量两个独立维度，混在一起打分是掩耳盗铃</font><font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">目标</font>**<font style="color:rgb(15, 17, 21);">：建立可量化、可回归的 RAG 评估管线，集成到现有 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-audit</font>`<font style="color:rgb(15, 17, 21);"> 的评估表中。</font>

#### <font style="color:rgb(15, 17, 21);">评估数据集</font>
<font style="color:rgb(15, 17, 21);">在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-admin</font>`<font style="color:rgb(15, 17, 21);"> 提供接口，录入“问题 → 标准答案 → 标准参考文档”三元组，存入 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rag_eval_case</font>`<font style="color:rgb(15, 17, 21);"> 表：</font>

<font style="color:rgb(15, 17, 21);">sql</font>

```plain
CREATE TABLE rag_eval_case (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  case_id VARCHAR(64) UNIQUE NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  question TEXT NOT NULL,
  expected_answer TEXT NOT NULL,
  expected_chunk_ids JSON COMMENT '预期应被检索到的 kb_chunk.id 列表',
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_agent (agent_id)
);
```

#### <font style="color:rgb(15, 17, 21);">评估执行器</font>
<font style="color:rgb(15, 17, 21);">评估作为异步任务运行，复用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTask</font>`<font style="color:rgb(15, 17, 21);"> 通道：</font>

1. <font style="color:rgb(15, 17, 21);">对每个 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rag_eval_case</font>`<font style="color:rgb(15, 17, 21);">，模拟一次完整的 Agent 调用（问题 → 检索 → 生成）。</font>
2. **<font style="color:rgb(15, 17, 21);">拦截检索结果</font>**<font style="color:rgb(15, 17, 21);">：记录 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 实际返回的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">chunk_id</font>`<font style="color:rgb(15, 17, 21);"> 列表。</font>
3. **<font style="color:rgb(15, 17, 21);">计算检索指标</font>**<font style="color:rgb(15, 17, 21);">：</font>
    - `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Context Precision</font>`<font style="color:rgb(15, 17, 21);">：实际检索到的 chunk 中，有多少在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">expected_chunk_ids</font>`<font style="color:rgb(15, 17, 21);"> 里，且排名是否靠前</font><font style="color:rgb(15, 17, 21);">。</font>
    - `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Context Recall</font>`<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">expected_chunk_ids</font>`<font style="color:rgb(15, 17, 21);"> 中有多少被实际检索到。</font>
4. **<font style="color:rgb(15, 17, 21);">计算生成指标</font>**<font style="color:rgb(15, 17, 21);">：</font>
    - `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Faithfulness</font>`<font style="color:rgb(15, 17, 21);">：用 LLM-as-a-Judge 判断回答是否只包含检索上下文支持的内容（防幻觉）。</font>
    - `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Answer Relevance</font>`<font style="color:rgb(15, 17, 21);">：判断回答是否直接回应了问题。</font>
5. <font style="color:rgb(15, 17, 21);">结果写入 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rag_eval_run</font>`<font style="color:rgb(15, 17, 21);"> 表，供 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-admin</font>`<font style="color:rgb(15, 17, 21);"> 的评估面板展示。</font>

#### <font style="color:rgb(15, 17, 21);">回归门禁</font>
<font style="color:rgb(15, 17, 21);">评估管线可以接入 CI：每次修改分块策略、Embedding 模型或检索参数后，自动跑一遍评估集。如果 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Context Precision</font>`<font style="color:rgb(15, 17, 21);"> 低于阈值（如 0.75）或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Faithfulness</font>`<font style="color:rgb(15, 17, 21);"> 下降超过 5%，阻止发布</font><font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">阶段四：GraphRAG——关系型与全局性查询</font>
**<font style="color:rgb(15, 17, 21);">对应博客内容</font>**<font style="color:rgb(15, 17, 21);">：传统 RAG 的结构性天花板是“只会找局部相关的文本片段，不会连点成线”</font><font style="color:rgb(15, 17, 21);">。问“过去一年客户投诉的反复主题有哪些”，没有单个文本块能回答，需要把不同文档中的信息串联起来</font><font style="color:rgb(15, 17, 21);">。GraphRAG 用知识图谱重建碎片之间的关系网络</font><font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">目标</font>**<font style="color:rgb(15, 17, 21);">：在知识库中增加实体关系抽取和图谱检索能力，作为 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 的补充工具。</font>

#### <font style="color:rgb(15, 17, 21);">离线图谱构建</font>
<font style="color:rgb(15, 17, 21);">在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DocumentIndexTaskHandler</font>`<font style="color:rgb(15, 17, 21);"> 中增加一个可选步骤（通过 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_knowledge_base</font>`<font style="color:rgb(15, 17, 21);"> 的配置开关控制）：</font>

1. <font style="color:rgb(15, 17, 21);">调用强模型（如 Qwen-Max）从每个 chunk 中抽取三元组 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">(Subject, Predicate, Object)</font>`<font style="color:rgb(15, 17, 21);">。</font>
2. <font style="color:rgb(15, 17, 21);">实体去重和归一化。</font>
3. <font style="color:rgb(15, 17, 21);">写入图数据库（Neo4j 或 Milvus 的图能力）。</font>**<font style="color:rgb(15, 17, 21);">建议用 Neo4j</font>**<font style="color:rgb(15, 17, 21);">，因为 GraphRAG 的查询模式（多跳邻居、社区摘要）不是向量库擅长的</font><font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">关键约束</font>**<font style="color:rgb(15, 17, 21);">：GraphRAG 的构建成本极高（LLM 抽取实体容易产生幻觉，且需要人工校验）。</font>**<font style="color:rgb(15, 17, 21);">阶段四必须在前三阶段稳定运行、且业务确实出现了“关系型/全局型查询”需求后才启动。</font>**

#### <font style="color:rgb(15, 17, 21);">在线图谱检索工具</font>
<font style="color:rgb(15, 17, 21);">新增 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GraphSearchTool implements ProductTool</font>`<font style="color:rgb(15, 17, 21);">：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```plain
@Tool(name = "graph_search",
      description = "当需要查找实体之间的关系、多跳推理或全局性主题归纳时使用此工具。")
public String graphSearch(
    @ToolParam(description = "实体名称或关系查询") String query,
    RuntimeContext ctx
) { ... }
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GraphSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 内部：</font>

+ <font style="color:rgb(15, 17, 21);">从查询中提取实体（可用轻量 NER 或直接让查询匹配图谱节点）。</font>
+ <font style="color:rgb(15, 17, 21);">在 Neo4j 中检索 N 度邻居子图。</font>
+ <font style="color:rgb(15, 17, 21);">将子图转化为自然语言描述，返回给 Agent。</font>

**<font style="color:rgb(15, 17, 21);">Agent 的路由</font>**<font style="color:rgb(15, 17, 21);">：Agent 在系统 Prompt 中被引导：“如果问题涉及精确编号，用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">knowledge_search</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KEYWORD</font>`<font style="color:rgb(15, 17, 21);"> 模式；如果涉及语义查找，用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HYBRID</font>`<font style="color:rgb(15, 17, 21);">；如果涉及实体关系和全局归纳，用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">graph_search</font>`<font style="color:rgb(15, 17, 21);">。”Agent 自主决策调用哪个工具——这是 Agentic RAG 的核心设计：检索工具是可选的，由 Agent 根据查询复杂度动态决定</font><font style="color:rgb(15, 17, 21);">。</font>

## <font style="color:rgb(15, 17, 21);">三、数据表设计</font>
### <font style="color:rgb(15, 17, 21);">3.1 知识库管理表（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-state</font>`<font style="color:rgb(15, 17, 21);"> 的 Flyway）</font>
<font style="color:rgb(15, 17, 21);">sql</font>

```plain
-- 知识库定义
CREATE TABLE kb_knowledge_base (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  kb_id VARCHAR(64) UNIQUE NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  graph_enabled TINYINT(1) DEFAULT 0 COMMENT '阶段四开关',
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
);

-- 文档
CREATE TABLE kb_document (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  doc_id VARCHAR(64) UNIQUE NOT NULL,
  kb_id VARCHAR(64) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  status VARCHAR(32) DEFAULT 'PENDING', -- PENDING / PROCESSING / SUCCESS / FAILED
  error_msg TEXT,
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
);

-- 切块（文本真相源）
CREATE TABLE kb_chunk (
  id VARCHAR(64) PRIMARY KEY,          -- 与 Milvus knowledge Collection 的 id 一致
  kb_id VARCHAR(64) NOT NULL,
  doc_id VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  chunk_index INT NOT NULL,
  token_count INT,
  FULLTEXT idx_content (content)       -- 阶段二 BM25 前提
);
```

### <font style="color:rgb(15, 17, 21);">3.2 评估表（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-audit</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-state</font>`<font style="color:rgb(15, 17, 21);">）</font>
<font style="color:rgb(15, 17, 21);">sql</font>

```plain
CREATE TABLE rag_eval_case (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  case_id VARCHAR(64) UNIQUE NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  question TEXT NOT NULL,
  expected_answer TEXT NOT NULL,
  expected_chunk_ids JSON,
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE rag_eval_run (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  run_id VARCHAR(64) UNIQUE NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  total_cases INT NOT NULL,
  context_precision DECIMAL(6,4),
  context_recall DECIMAL(6,4),
  faithfulness DECIMAL(6,4),
  answer_relevance DECIMAL(6,4),
  status VARCHAR(32) DEFAULT 'RUNNING',
  started_at DATETIME(3),
  finished_at DATETIME(3)
);
```

**<font style="color:rgb(15, 17, 21);">注意</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">episodic_memory</font>`<font style="color:rgb(15, 17, 21);"> Collection 与 RAG 知识库是</font>**<font style="color:rgb(15, 17, 21);">两套独立的向量空间</font>**<font style="color:rgb(15, 17, 21);">。记忆的向量化由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MemoryExtractionMiddleware</font>`<font style="color:rgb(15, 17, 21);"> 负责，知识库的向量化由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DocumentIndexTaskHandler</font>`<font style="color:rgb(15, 17, 21);"> 负责。两者共用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">VectorStore</font>`<font style="color:rgb(15, 17, 21);"> 接口，但 Collection 不同，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Filter</font>`<font style="color:rgb(15, 17, 21);"> 也不同（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeFilter</font>`<font style="color:rgb(15, 17, 21);"> vs </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MemoryFilter</font>`<font style="color:rgb(15, 17, 21);">）。</font>

## <font style="color:rgb(15, 17, 21);">四、关键代码</font>
### <font style="color:rgb(15, 17, 21);">4.1 RRF 融合（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 内部）</font>
<font style="color:rgb(15, 17, 21);">java</font>

```plain
public class RrfFuser {
    private static final int K = 60;

    public List<ChunkScore> fuse(List<String> vectorIds, List<String> bm25Ids) {
        Map<String, Double> scores = new HashMap<>();
        IntStream.range(0, vectorIds.size())
            .forEach(i -> scores.merge(vectorIds.get(i), 1.0 / (K + i + 1), Double::sum));
        IntStream.range(0, bm25Ids.size())
            .forEach(i -> scores.merge(bm25Ids.get(i), 1.0 / (K + i + 1), Double::sum));
        return scores.entrySet().stream()
            .map(e -> new ChunkScore(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingDouble(ChunkScore::score).reversed())
            .limit(10)
            .toList();
    }
}
```

### <font style="color:rgb(15, 17, 21);">4.2 Embedding 服务的位置</font>
<font style="color:rgb(15, 17, 21);">Embedding </font>**<font style="color:rgb(15, 17, 21);">不在</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 中执行（README 已明确“本模块不认识模型”）。建议在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic/infrastructure/embedding</font>`<font style="color:rgb(15, 17, 21);"> 中实现 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">EmbeddingService</font>`<font style="color:rgb(15, 17, 21);">，由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DocumentIndexTaskHandler</font>`<font style="color:rgb(15, 17, 21);"> 共同调用。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 只接收 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">float[]</font>`<font style="color:rgb(15, 17, 21);"> 向量。</font>

### <font style="color:rgb(15, 17, 21);">4.3 评估任务（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime/task</font>`<font style="color:rgb(15, 17, 21);">）</font>
<font style="color:rgb(15, 17, 21);">java</font>

```plain
public class RagEvalTaskHandler implements AgentTaskHandler {
    @Override
    public void handle(AgentTask task) {
        List<RagEvalCase> cases = evalCaseRepo.findByAgentId(task.agentId());
        for (RagEvalCase c : cases) {
            // 1. 执行 Agent 调用，拦截 KnowledgeSearchTool 的实际 chunk 命中
            // 2. 计算 Context Precision / Recall
            // 3. LLM-as-a-Judge 计算 Faithfulness / Answer Relevance
            // 4. 写入 rag_eval_run
        }
    }
}
```

## <font style="color:rgb(15, 17, 21);">五、注意事项</font>
**<font style="color:rgb(15, 17, 21);">1. </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>**`**<font style="color:rgb(15, 17, 21);"> 的 schema 不要动。</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">knowledge</font>`<font style="color:rgb(15, 17, 21);"> 的字段固定为 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">id</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">vector</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">user_id</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent_id</font>`<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">episodic_memory</font>`<font style="color:rgb(15, 17, 21);"> 额外带 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">tenant_id</font>`<font style="color:rgb(15, 17, 21);">。这是模块契约。混合检索的 BM25 路走 MySQL，不往 Milvus 里塞稀疏向量——</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 保持纯粹。</font>

**<font style="color:rgb(15, 17, 21);">2. RRF 和 Rerank 在 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic</font>**`**<font style="color:rgb(15, 17, 21);"> 内部完成，不穿透到 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>**`**<font style="color:rgb(15, 17, 21);">。</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 只负责“给向量、拿 ID”，融合和精排是 RAG 工具的业务逻辑。</font>

**<font style="color:rgb(15, 17, 21);">3. 阶段二先不追求完美的分块。</font>**<font style="color:rgb(15, 17, 21);"> 固定长度 + 重叠的切块足够验证混合检索链路。分块策略的优化（语义切块、父子文档索引）放在评估体系建立之后，用评估指标驱动优化，而不是凭感觉调参</font><font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">4. 评估必须分离维度。</font>**<font style="color:rgb(15, 17, 21);"> 一个综合分数 0.82 没有意义——团队需要知道当它跌到 0.74 时该先查检索还是查生成</font><font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Context Precision</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Faithfulness</font>`<font style="color:rgb(15, 17, 21);"> 必须独立报告。</font>

**<font style="color:rgb(15, 17, 21);">5. GraphRAG 不要提前上。</font>**<font style="color:rgb(15, 17, 21);"> 实体抽取的 LLM 调用成本和幻觉风险都很高。前三阶段稳定运行、且业务确实出现关系型查询需求后，再在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">kb_knowledge_base.graph_enabled</font>`<font style="color:rgb(15, 17, 21);"> 打开图谱构建。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-vector</font>`<font style="color:rgb(15, 17, 21);"> 本身不需要为 GraphRAG 改动——图数据存 Neo4j，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GraphSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic</font>`<font style="color:rgb(15, 17, 21);"> 中独立实现。</font>

**<font style="color:rgb(15, 17, 21);">6. Agentic RAG 的“工具描述”决定路由质量。</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">KnowledgeSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GraphSearchTool</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Tool(description = ...)</font>`<font style="color:rgb(15, 17, 21);"> 必须写清楚适用场景和输入期望。Agent 靠这个描述决定调哪个工具。描述模糊会导致 Agent 乱调工具或该调不调。</font>

