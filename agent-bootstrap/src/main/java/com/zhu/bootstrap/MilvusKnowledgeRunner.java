package com.zhu.bootstrap;

import com.zhu.vector.infrastructure.MilvusCollections;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 连通性通过之后准备知识集合和情景记忆集合。测试关掉启动依赖时不创建，避免去连 Milvus。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusKnowledgeRunner implements ApplicationRunner {

    private final MilvusClientV2 client;

    private final String collection;

    private final int dimensions;

    /**
     * 复用进程里的客户端。集合名和维度有默认值，不在仓库里写主机。
     */
    public MilvusKnowledgeRunner(
            MilvusClientV2 client,
            @Value("${agentscope.milvus.collection:knowledge}") String collection,
            @Value("${agentscope.milvus.dimensions:1024}") int dimensions) {
        this.client = client;
        this.collection = collection;
        this.dimensions = dimensions;
    }

    /** 建集合失败就让进程退出。不在这里写入或检索。 */
    @Override
    public void run(ApplicationArguments args) {
        MilvusCollections.ensure(client, collection, dimensions);
    }
}
