package com.zhu.objectstore;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.zhu.objectstore.infrastructure.ObjectKeys;
import com.zhu.objectstore.infrastructure.OssObjectStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OSS 实现：前缀拼装与业务键回传。不连真桶。
 */
class OssObjectStoreTest {

    /** put 返回无前缀业务键，实际请求键带前缀。 */
    @Test
    void putUsesPrefixedOssKeyButReturnsBusinessKey() {
        OSSClient client = mock(OSSClient.class);
        when(client.putObject(any(PutObjectRequest.class))).thenReturn(mock(PutObjectResult.class));
        OssObjectStore store =
                new OssObjectStore(client, "dream-agentscope-dev", ObjectKeys.normalizePrefix("agentscope/"));

        String returned = store.put("kb/t/u/a.txt", new byte[] {1, 2});
        assertThat(returned).isEqualTo("kb/t/u/a.txt");
        assertThat(store.storageType()).isEqualTo("OSS");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("dream-agentscope-dev");
        assertThat(captor.getValue().key()).isEqualTo("agentscope/kb/t/u/a.txt");
    }

    /** delete 带同一前缀。 */
    @Test
    void deleteUsesPrefixedOssKey() {
        OSSClient client = mock(OSSClient.class);
        OssObjectStore store = new OssObjectStore(client, "b", "agentscope/");
        store.delete("kb/x");
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("agentscope/kb/x");
    }
}
