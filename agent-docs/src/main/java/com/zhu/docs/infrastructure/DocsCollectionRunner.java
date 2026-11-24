package com.zhu.docs.infrastructure;

import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 用户进程连上 Milvus 之后准备 docs_chunks。测试没有客户端时不创建。
 * 字段或维度不对就让进程退出。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnBean(MilvusClientV2.class)
public class DocsCollectionRunner implements ApplicationRunner {

    private final MilvusClientV2 client;

    /** 客户端由向量模块创建，这里只建文档集合。 */
    public DocsCollectionRunner(MilvusClientV2 client) {
        this.client = client;
    }

    /** 不写入向量。失败信息里不带地址和令牌。 */
    @Override
    public void run(ApplicationArguments args) {
        DocsCollections.ensure(client);
    }
}
