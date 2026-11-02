package com.zhu.objectstore.infrastructure;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.api.ObjectStore;
import java.util.logging.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 基础设施：S3 兼容实现。type=s3 与 type=minio 共用。
 * 对外仍返回无前缀业务键；SDK 侧自动加配置前缀。
 */
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = Logger.getLogger(S3ObjectStore.class.getName());

    private final S3Client client;

    private final String bucket;

    private final String prefix;

    private final String storageType;

    /**
     * @param client      进程内复用的 S3 客户端
     * @param bucket      桶名
     * @param prefix      已规范化前缀，可空
     * @param storageType 附件表取值，S3 或 MINIO
     */
    public S3ObjectStore(S3Client client, String bucket, String prefix, String storageType) {
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix;
        this.storageType = storageType == null || storageType.isBlank() ? "S3" : storageType;
    }

    /**
     * 写入对象。失败只打短日志，不落密钥或对象正文。
     */
    @Override
    public String put(String key, byte[] content) {
        String businessKey = ObjectKeys.normalize(key);
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(businessKey))
                            .build(),
                    RequestBody.fromBytes(content == null ? new byte[0] : content));
            return businessKey;
        } catch (RuntimeException ex) {
            log.warning("object store put failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store put failed");
        }
    }

    /**
     * 不存在返回 null；其它错误按不可用。
     */
    @Override
    public byte[] get(String key) {
        String businessKey = ObjectKeys.normalize(key);
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(businessKey))
                    .build()).asByteArray();
        } catch (NoSuchKeyException ex) {
            return null;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return null;
            }
            log.warning("object store get failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store get failed");
        } catch (RuntimeException ex) {
            log.warning("object store get failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store get failed");
        }
    }

    /**
     * 删除。不存在视为成功。
     */
    @Override
    public void delete(String key) {
        String businessKey = ObjectKeys.normalize(key);
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(businessKey))
                    .build());
        } catch (NoSuchKeyException ex) {
            return;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return;
            }
            log.warning("object store delete failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store delete failed");
        } catch (RuntimeException ex) {
            log.warning("object store delete failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store delete failed");
        }
    }

    @Override
    public String storageType() {
        return storageType;
    }

    /** 业务键前面加上配置前缀，形成桶内真实对象名。 */
    String objectKey(String businessKey) {
        return prefix + businessKey;
    }
}
