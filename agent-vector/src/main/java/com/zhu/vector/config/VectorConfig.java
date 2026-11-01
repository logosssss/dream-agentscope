package com.zhu.vector.config;

import com.zhu.vector.infrastructure.MilvusCollections;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户进程里只放一个 Milvus 客户端。测试关掉启动依赖时不创建。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class VectorConfig {

    /**
     * 进程内复用这一条 gRPC 通道。容器关闭时 close。
     * 地址和令牌只来自环境变量，不写进异常。
     */
    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(
            @Value("${agentscope.milvus.uri:}") String uri,
            @Value("${agentscope.milvus.token:}") String token) {
        return MilvusCollections.connect(uri, token);
    }
}
