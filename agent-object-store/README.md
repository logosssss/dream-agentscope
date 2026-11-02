# agent-object-store

对象存储。只负责按相对键读写字节。本模块不认识知识库、附件表或模型。

坐标：`com.zhu:agent-object-store`。包名：`com.zhu.objectstore`。

## 依赖

依赖 `agent-common`、`spring-boot-autoconfigure`、`alibabacloud-oss-v2`、`software.amazon.awssdk:s3`。

不能依赖 `agent-state`、`agent-semantic`、`agent-runtime`、`agent-access`、`agent-audit`、`agent-admin`、`agent-vector`。

管理进程不要依赖本模块。

## 包

| 包 | 类型 | 现状 |
| --- | --- | --- |
| `api` | `ObjectStore` | 写入、读取、删除与 `storageType` 缝 |
| `infrastructure` | `LocalObjectStore` | `type=local`（默认）。根目录 `agentscope.object-store.root`，默认 `data/objects` |
| `infrastructure` | `OssObjectStore` | `type=oss`。官方 SDK V2；对外业务键不含前缀 |
| `infrastructure` | `S3ObjectStore` | `type=s3` / `minio`。AWS SDK V2；MinIO 默认 path-style |
| `config` | `OssObjectStoreConfig` | 装配 OSS 客户端。缺 endpoint/region/bucket/AK/SK 启动失败 |
| `config` | `S3ObjectStoreConfig` | 装配 S3 兼容客户端。缺 endpoint/region/bucket/AK/SK 启动失败 |

## 配置

| 项 | 环境变量 | 说明 |
| --- | --- | --- |
| `agentscope.object-store.type` | `AGENTSCOPE_OBJECT_STORE_TYPE` | `local` / `oss` / `s3` / `minio`，默认 `local` |
| `agentscope.object-store.root` | `AGENTSCOPE_OBJECT_STORE_ROOT` | 仅 local |
| `agentscope.object-store.oss.endpoint` | `AGENTSCOPE_OSS_ENDPOINT` | 如 `https://oss-cn-shenzhen.aliyuncs.com` |
| `agentscope.object-store.oss.region` | `AGENTSCOPE_OSS_REGION` | 如 `cn-shenzhen` |
| `agentscope.object-store.oss.bucket` | `AGENTSCOPE_OSS_BUCKET` | 桶名 |
| `agentscope.object-store.oss.access-key-id` | `AGENTSCOPE_OSS_ACCESS_KEY_ID` | 密钥，勿进仓库 |
| `agentscope.object-store.oss.access-key-secret` | `AGENTSCOPE_OSS_ACCESS_KEY_SECRET` | 密钥，勿进仓库 |
| `agentscope.object-store.oss.prefix` | `AGENTSCOPE_OSS_PREFIX` | 默认 `agentscope/` |
| `agentscope.object-store.s3.endpoint` | `AGENTSCOPE_S3_ENDPOINT` | 如 `https://s3.amazonaws.com` 或 `http://127.0.0.1:9000` |
| `agentscope.object-store.s3.region` | `AGENTSCOPE_S3_REGION` | 如 `us-east-1` |
| `agentscope.object-store.s3.bucket` | `AGENTSCOPE_S3_BUCKET` | 桶名 |
| `agentscope.object-store.s3.access-key-id` | `AGENTSCOPE_S3_ACCESS_KEY_ID` | 密钥，勿进仓库 |
| `agentscope.object-store.s3.access-key-secret` | `AGENTSCOPE_S3_ACCESS_KEY_SECRET` | 密钥，勿进仓库 |
| `agentscope.object-store.s3.prefix` | `AGENTSCOPE_S3_PREFIX` | 默认 `agentscope/` |
| `agentscope.object-store.s3.path-style` | `AGENTSCOPE_S3_PATH_STYLE` | 空则 minio 开、s3 关 |

## 还没有

连接串仍走环境变量，不进 Nacos。通用附件 multipart 写入和正文下载已在接入层。知识库删文档/删库时按 DOCUMENT 引用清对象，也在接入层。
