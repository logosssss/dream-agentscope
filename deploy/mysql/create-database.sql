-- 用户进程 Flyway 使用的库。先建库再启动；表由迁移脚本创建，不要手工建表。
CREATE DATABASE IF NOT EXISTS agentscope
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
