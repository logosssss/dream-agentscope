package com.zhu.objectstore;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.config.S3ObjectStoreConfig;
import com.zhu.objectstore.infrastructure.ObjectKeys;
import com.zhu.objectstore.infrastructure.S3ObjectStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S3 兼容实现：前缀拼装、业务键回传与缺对象。不连真桶。
 */
class S3ObjectStoreTest {

    /** put 返回无前缀业务键，实际请求键带前缀。 */
    @Test
    void putUsesPrefixedKeyButReturnsBusinessKey() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3ObjectStore store = new S3ObjectStore(
                client, "bucket", ObjectKeys.normalizePrefix("agentscope/"), "S3");

        String returned = store.put("kb/t/u/a.txt", new byte[] {1, 2});
        assertThat(returned).isEqualTo("kb/t/u/a.txt");
        assertThat(store.storageType()).isEqualTo("S3");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("bucket");
        assertThat(captor.getValue().key()).isEqualTo("agentscope/kb/t/u/a.txt");
    }

    /** delete 带同一前缀。MinIO 的 storage_type 与 S3 区分开。 */
    @Test
    void deleteUsesPrefixedKey() {
        S3Client client = mock(S3Client.class);
        S3ObjectStore store = new S3ObjectStore(client, "b", "agentscope/", "MINIO");
        store.delete("kb/x");
        assertThat(store.storageType()).isEqualTo("MINIO");
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("agentscope/kb/x");
    }

    /** 对象不存在按空，不包装成不可用。 */
    @Test
    void getMissingKeyReturnsNull() {
        S3Client client = mock(S3Client.class);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").statusCode(404).build());
        S3ObjectStore store = new S3ObjectStore(client, "b", "agentscope/", "S3");
        assertThat(store.get("kb/missing")).isNull();
    }

    /** 读到的字节原样返回。 */
    @Test
    void getReturnsBytes() {
        S3Client client = mock(S3Client.class);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {9}));
        S3ObjectStore store = new S3ObjectStore(client, "b", "", "S3");
        assertThat(store.get("att/t/u/a.bin")).containsExactly(9);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("att/t/u/a.bin");
    }

    /** SDK 失败按不可用，不把异常正文对外。 */
    @Test
    void putFailureIsUnavailable() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("down"));
        S3ObjectStore store = new S3ObjectStore(client, "b", "", "S3");
        assertThatThrownBy(() -> store.put("kb/a", new byte[] {1}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.UNAVAILABLE);
    }

    /** MinIO 默认开 path-style；S3 默认关。显式配置覆盖默认。 */
    @Test
    void pathStyleDefaultsByType() {
        assertThat(S3ObjectStoreConfig.pathStyle("minio", "")).isTrue();
        assertThat(S3ObjectStoreConfig.pathStyle("s3", null)).isFalse();
        assertThat(S3ObjectStoreConfig.pathStyle("s3", "true")).isTrue();
        assertThat(S3ObjectStoreConfig.storageType("minio")).isEqualTo("MINIO");
        assertThat(S3ObjectStoreConfig.storageType("s3")).isEqualTo("S3");
    }

    /** 端点必须是带主机的绝对 URI，避免启动后再连错地址。 */
    @Test
    void endpointMustBeAbsoluteUri() {
        assertThat(S3ObjectStoreConfig.endpointUri("http://127.0.0.1:9000").getHost()).isEqualTo("127.0.0.1");
        assertThatThrownBy(() -> S3ObjectStoreConfig.endpointUri("127.0.0.1:9000"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> S3ObjectStoreConfig.endpointUri(" "))
                .isInstanceOf(IllegalStateException.class);
    }
}
