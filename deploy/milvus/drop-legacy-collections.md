# 删掉字段不合规的旧集合

用户进程要求：

- `knowledge`：`id`、`vector`、`user_id`、`agent_id`，向量维度 1024
- `episodic_memory`：`id`、`vector`、`tenant_id`、`user_id`、`agent_id`，维度 1024

已有集合缺字段或维度不对时，进程会退出且**不会自动改表**。当前还没有写入向量，可以直接删掉重建。

## Attu

打开对应库 → Collections → 删除 `knowledge`（若有 `episodic_memory` 且结构不对也删）→ 再启动用户进程。

## Python（pymilvus）

```python
from pymilvus import connections, utility

connections.connect(uri="http://你的主机:19530", token="用户名:密码")  # 无鉴权则不要 token
for name in ("knowledge", "episodic_memory"):
    if utility.has_collection(name):
        utility.drop_collection(name)
        print("dropped", name)
```

删完后重新：

```powershell
powershell -File scripts\run-bootstrap.ps1
```

启动成功后会按正确字段新建集合。
