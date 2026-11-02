package com.zhu.objectstore.infrastructure;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.api.ObjectStore;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * 基础设施：阿里云 OSS 实现。type=oss 时由配置装配。
 * 对外仍返回无前缀业务键；SDK 侧自动加配置前缀。
 */
public class OssObjectStore implements ObjectStore {

    private static final Logger log = Logger.getLogger(OssObjectStore.class.getName());

    private final OSSClient client;

    private final String bucket;

    private final String prefix;

    /**
     * @param client 进程内复用的 OSS 客户端
     * @param bucket 桶名
     * @param prefix 已规范化前缀，可空
     */
    public OssObjectStore(OSSClient client, String bucket, String prefix) {
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix;
    }

    /**
     * 写入对象。失败只打短日志，不落密钥或对象正文。
     */
    @Override
    public String put(String key, byte[] content) {
        String businessKey = ObjectKeys.normalize(key);
        try {
            client.putObject(PutObjectRequest.newBuilder()
                    .bucket(bucket)
                    .key(ossKey(businessKey))
                    .body(BinaryData.fromBytes(content == null ? new byte[0] : content))
                    .build());
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
        try (GetObjectResult result = client.getObject(GetObjectRequest.newBuilder()
                .bucket(bucket)
                .key(ossKey(businessKey))
                .build())) {
            InputStream body = result.body();
            if (body == null) {
                return new byte[0];
            }
            return body.readAllBytes();
        } catch (ServiceException ex) {
            if (ex.statusCode() == 404 || "NoSuchKey".equalsIgnoreCase(ex.errorCode())) {
                return null;
            }
            log.warning("object store get failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store get failed");
        } catch (IOException ex) {
            log.warning("object store get failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store get failed");
        } catch (RuntimeException ex) {
            ServiceException service = ServiceException.asCause(ex);
            if (service != null
                    && (service.statusCode() == 404 || "NoSuchKey".equalsIgnoreCase(service.errorCode()))) {
                return null;
            }
            log.warning("object store get failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store get failed");
        } catch (Exception ex) {
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
            client.deleteObject(DeleteObjectRequest.newBuilder()
                    .bucket(bucket)
                    .key(ossKey(businessKey))
                    .build());
        } catch (ServiceException ex) {
            if (ex.statusCode() == 404 || "NoSuchKey".equalsIgnoreCase(ex.errorCode())) {
                return;
            }
            log.warning("object store delete failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store delete failed");
        } catch (RuntimeException ex) {
            ServiceException service = ServiceException.asCause(ex);
            if (service != null
                    && (service.statusCode() == 404 || "NoSuchKey".equalsIgnoreCase(service.errorCode()))) {
                return;
            }
            log.warning("object store delete failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store delete failed");
        }
    }

    @Override
    public String storageType() {
        return "OSS";
    }

    /** 业务键前面加上配置前缀，形成桶内真实对象名。 */
    String ossKey(String businessKey) {
        return prefix + businessKey;
    }
}
