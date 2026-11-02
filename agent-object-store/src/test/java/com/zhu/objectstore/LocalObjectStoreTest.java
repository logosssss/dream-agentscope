package com.zhu.objectstore;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.infrastructure.LocalObjectStore;
import com.zhu.objectstore.infrastructure.ObjectKeys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地对象读写与路径护栏。不连 OSS。
 */
class LocalObjectStoreTest {

    @TempDir
    Path temp;

    /** 写入后能原样读回，删后读空。 */
    @Test
    void putGetDeleteRoundTrip() {
        LocalObjectStore store = new LocalObjectStore(temp.toString());
        String key = store.put("kb/t1/u1/a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        assertThat(key).isEqualTo("kb/t1/u1/a.txt");
        assertThat(store.storageType()).isEqualTo("LOCAL");
        assertThat(store.get(key)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.isRegularFile(temp.resolve("kb/t1/u1/a.txt"))).isTrue();
        store.delete(key);
        assertThat(store.get(key)).isNull();
    }

    /** 含 .. 或空键直接拒绝，不触盘。 */
    @Test
    void rejectsTraversalAndBlankKey() {
        LocalObjectStore store = new LocalObjectStore(temp.toString());
        assertThatThrownBy(() -> store.put("../x", new byte[] {1}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> store.get(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    /** 前缀规范化：空白为空；非空补尾斜杠。 */
    @Test
    void normalizesPrefix() {
        assertThat(ObjectKeys.normalizePrefix(null)).isEmpty();
        assertThat(ObjectKeys.normalizePrefix("agentscope")).isEqualTo("agentscope/");
        assertThat(ObjectKeys.normalizePrefix("agentscope/")).isEqualTo("agentscope/");
    }
}
