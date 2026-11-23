# agent-test-support

测试替身与夹具。**不发布、不进部署包**，只以 `test-jar` 形式出现在各模块的测试类路径上。

## 坐标与依赖

```xml
<dependency>
  <groupId>com.zhu</groupId>
  <artifactId>agent-test-support</artifactId>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

模块编译期只依赖 `agent-product-api` 与 `agentscope-harness`；不再依赖 `agent-runtime`，否则
`runtime → test-support → runtime` 会形成 reactor 环。夹具里需要 `AgentRequest` 时，由用到它的测试
自己拼（字段顺序一处一变就会编译报错的成本，比分模块环更低）。

## 内容

| 类 | 用途 |
|----|------|
| `com.zhu.testsupport.StubProductModule` | `ProductModule` 的替身：`main(agentId)` / `secondary(agentId)` 两个工厂；`customize` / `attachTools` 只往 `events()` 里记一条，不碰 Harness 真对象 |

## 约定

- 替身只放「形状」，不放断言逻辑；断言留在用它的测试里。
- 新增夹具前先确认没有模块依赖环——`agent-runtime` 的产品缝测试就用本模块，属于合法方向
  （runtime 的测试类路径依赖 test-support，test-support 编译期只看 product-api）。
- 这里不放 Spring 上下文基类。需要上下文的测试（降级启动、装配）继续留在 `agent-bootstrap`，
  因为它们要扫真实的 `com.zhu` 全包。

## 还没有

- Testcontainers 基类（本机无 Docker，未做）。
- Spring slice 基类（当前唯一的上下文测试在 `agent-bootstrap`，抽出来收益不明显）。
