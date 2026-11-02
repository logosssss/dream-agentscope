package com.zhu.objectstore.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.zhu.objectstore.api.ObjectStore;
import com.zhu.objectstore.infrastructure.ObjectKeys;
import com.zhu.objectstore.infrastructure.OssObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * type=oss 时装配官方 SDK V2 客户端与 OssObjectStore。
 * endpoint / region / bucket / AK/SK 任一缺失则拒绝启动，不静默回落 local。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.object-store.type", havingValue = "oss")
public class OssObjectStoreConfig {

    /**
     * 进程内复用一条客户端。容器销毁时 close。
     * 密钥只从配置注入，不写进日志。
     */
    @Bean(destroyMethod = "close")
    public OSSClient ossClient(
            @Value("${agentscope.object-store.oss.endpoint:}") String endpoint,
            @Value("${agentscope.object-store.oss.region:}") String region,
            @Value("${agentscope.object-store.oss.access-key-id:}") String accessKeyId,
            @Value("${agentscope.object-store.oss.access-key-secret:}") String accessKeySecret) {
        String ep = required(endpoint, "AGENTSCOPE_OSS_ENDPOINT");
        String rg = required(region, "AGENTSCOPE_OSS_REGION");
        String ak = required(accessKeyId, "AGENTSCOPE_OSS_ACCESS_KEY_ID");
        String sk = required(accessKeySecret, "AGENTSCOPE_OSS_ACCESS_KEY_SECRET");
        return OSSClient.newBuilder()
                .region(rg)
                .endpoint(ep)
                .credentialsProvider(new StaticCredentialsProvider(ak, sk))
                .build();
    }

    /**
     * 唯一的 ObjectStore 实现。前缀默认 agentscope/。
     */
    @Bean
    public ObjectStore objectStore(
            OSSClient ossClient,
            @Value("${agentscope.object-store.oss.bucket:}") String bucket,
            @Value("${agentscope.object-store.oss.prefix:agentscope/}") String prefix) {
        String bk = required(bucket, "AGENTSCOPE_OSS_BUCKET");
        return new OssObjectStore(ossClient, bk, ObjectKeys.normalizePrefix(prefix));
    }

    private static String required(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("agentscope.object-store.type=oss requires " + envName);
        }
        return value.trim();
    }
}
