# agent-db

全仓库 **Flyway** 脚本唯一来源。只含 `src/main/resources/db/migration`，**无业务 Java**。

坐标：`com.zhu:agent-db`。

## 约定

- **仅用户进程**（`agent-bootstrap`）依赖本模块并执行迁移；管理进程不跑 Flyway、不依赖本模块。
- 表结构真相在此；业务 Mapper/XML 在 access / admin / runtime / state / semantic 等模块（原共享 `agent-audit` 已拆光，见 [`docs/audit-split-落地.md`](../docs/audit-split-落地.md)）。
- 不在此写业务 SQL 以外的代码。

## 测试

- `MigrationCatalogTest`：V1～V41 连续齐全。
- `SchemaContractTest`：双端（/三端）同表 Mapper 存在，且 XML `column=` ⊆ Flyway 推演列集。

## 还没有

- （本模块清单已清）
