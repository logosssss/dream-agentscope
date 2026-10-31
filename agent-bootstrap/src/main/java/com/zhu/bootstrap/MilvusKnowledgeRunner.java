package com.zhu.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 连通性通过之后准备知识集合。测试关掉启动依赖时不创建，避免去连 Milvus。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusKnowledgeRunner implements ApplicationRunner {

    private final String uri;

    private final String token;

    private final String collection;

    private final int dimensions;

    /**
     * 地址和令牌只来自环境变量。集合名和维度有默认值，不在仓库里写主机。
     */
    public MilvusKnowledgeRunner(
            @Value("${agentscope.milvus.uri:}") String uri,
            @Value("${agentscope.milvus.token:}") String token,
            @Value("${agentscope.milvus.collection:knowledge}") String collection,
            @Value("${agentscope.milvus.dimensions:1024}") int dimensions) {
        this.uri = uri;
        this.token = token;
        this.collection = collection;
        this.dimensions = dimensions;
    }

    /** 建集合失败就让进程退出。不在这里写入或检索。 */
    @Override
    public void run(ApplicationArguments args) {
        MilvusKnowledge.ensure(uri, token, collection, dimensions);
    }
}
