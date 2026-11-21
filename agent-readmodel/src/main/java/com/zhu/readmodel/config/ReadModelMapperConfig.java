package com.zhu.readmodel.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 共享表 Mapper 扫描。
 * 用户进程与管理进程都扫这一个包：同一张表只有一份 Mapper 定义，
 * 不会因为两个模块各写一份而在同一容器里撞 Bean 名。
 * 只在 MySQL 打开时扫描——没有数据源时不应创建 SqlSession，降级启动必须无 Mapper。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@MapperScan("com.zhu.readmodel.persistence")
public class ReadModelMapperConfig {
}
