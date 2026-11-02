package com.zhu.objectstore.config;

import com.zhu.objectstore.api.ObjectStore;
import com.zhu.objectstore.infrastructure.ObjectKeys;
import com.zhu.objectstore.infrastructure.S3ObjectStore;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * type=s3 或 type=minio 时装配 AWS SDK V2 客户端与 S3ObjectStore。
 * endpoint / region / bucket / AK/SK 任一缺失则拒绝启动，不静默回落 local。
 */
@Configuration
@Conditional(S3ObjectStoreConfig.S3CompatibleType.class)
public class S3ObjectStoreConfig {

    /**
     * 只认 s3 与 minio，避免和 local / oss 抢唯一的 ObjectStore。
     */
    static class S3CompatibleType extends AnyNestedCondition {

        /** 解析配置阶段就决定，缺属性时整类不装。 */
        S3CompatibleType() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        /** Amazon S3 或兼容网关。 */
        @ConditionalOnProperty(name = "agentscope.object-store.type", havingValue = "s3")
        static class OnS3 {
        }

        /** MinIO 桶，默认 path-style。 */
        @ConditionalOnProperty(name = "agentscope.object-store.type", havingValue = "minio")
        static class OnMinio {
        }
    }

    /**
     * 进程内复用一条客户端。容器销毁时 close。
     * 密钥只从配置注入，不写进日志。MinIO 默认走 path-style。
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(
            @Value("${agentscope.object-store.type}") String type,
            @Value("${agentscope.object-store.s3.endpoint:}") String endpoint,
            @Value("${agentscope.object-store.s3.region:}") String region,
            @Value("${agentscope.object-store.s3.access-key-id:}") String accessKeyId,
            @Value("${agentscope.object-store.s3.access-key-secret:}") String accessKeySecret,
            @Value("${agentscope.object-store.s3.path-style:}") String pathStyle) {
        URI endpointUri = endpointUri(endpoint);
        String rg = required(region, "AGENTSCOPE_S3_REGION");
        String ak = required(accessKeyId, "AGENTSCOPE_S3_ACCESS_KEY_ID");
        String sk = required(accessKeySecret, "AGENTSCOPE_S3_ACCESS_KEY_SECRET");
        boolean forcePathStyle = pathStyle(type, pathStyle);
        return S3Client.builder()
                .region(Region.of(rg))
                .endpointOverride(endpointUri)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ak, sk)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(forcePathStyle)
                        .build())
                .build();
    }

    /**
     * 唯一的 ObjectStore 实现。前缀默认 agentscope/。
     */
    @Bean
    public ObjectStore objectStore(
            S3Client s3Client,
            @Value("${agentscope.object-store.type}") String type,
            @Value("${agentscope.object-store.s3.bucket:}") String bucket,
            @Value("${agentscope.object-store.s3.prefix:agentscope/}") String prefix) {
        String bk = required(bucket, "AGENTSCOPE_S3_BUCKET");
        return new S3ObjectStore(s3Client, bk, ObjectKeys.normalizePrefix(prefix), storageType(type));
    }

    /**
     * 没配 path-style 时：minio 开，s3 关。配了就按布尔值。
     */
    public static boolean pathStyle(String type, String configured) {
        if (configured == null || configured.isBlank()) {
            return "minio".equalsIgnoreCase(type);
        }
        return Boolean.parseBoolean(configured.trim());
    }

    /** 附件表取值。无法识别时按 S3。 */
    public static String storageType(String type) {
        return "minio".equalsIgnoreCase(type) ? "MINIO" : "S3";
    }

    /** 必须带 scheme。缺了或写错直接启动失败。 */
    public static URI endpointUri(String endpoint) {
        String ep = required(endpoint, "AGENTSCOPE_S3_ENDPOINT");
        URI uri;
        try {
            uri = URI.create(ep);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("agentscope.object-store.type=s3/minio requires AGENTSCOPE_S3_ENDPOINT");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("agentscope.object-store.type=s3/minio requires AGENTSCOPE_S3_ENDPOINT");
        }
        return uri;
    }

    private static String required(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("agentscope.object-store.type=s3/minio requires " + envName);
        }
        return value.trim();
    }
}
