package com.zhu.objectstore.infrastructure;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.api.ObjectStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基础设施：把对象写到本地根目录。默认 data/objects，已进 .gitignore。
 * type=local（或缺省）时启用；不连 OSS。
 */
@Component
@ConditionalOnProperty(name = "agentscope.object-store.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStore implements ObjectStore {

    private static final Logger log = Logger.getLogger(LocalObjectStore.class.getName());

    private final Path root;

    /**
     * 根目录相对进程工作目录解析。空白时用 data/objects。
     * 只有这一条构造器，避免容器去找无参构造。
     */
    public LocalObjectStore(@Value("${agentscope.object-store.root:data/objects}") String root) {
        String configured = root == null || root.isBlank() ? "data/objects" : root.trim();
        this.root = Path.of(configured).toAbsolutePath().normalize();
    }

    /**
     * 先确保父目录，再整文件覆盖写入。
     * IO 失败抛 UNAVAILABLE，不把绝对路径带进对外文案。
     */
    @Override
    public String put(String key, byte[] content) {
        String normalized = ObjectKeys.normalize(key);
        Path target = resolveUnderRoot(normalized);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content == null ? new byte[0] : content);
            return normalized;
        } catch (IOException ex) {
            log.warning("object store put failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store put failed");
        }
    }

    /**
     * 文件不存在返回 null；读失败按不可用。
     */
    @Override
    public byte[] get(String key) {
        Path target = resolveUnderRoot(ObjectKeys.normalize(key));
        if (!Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            log.warning("object store get failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store get failed");
        }
    }

    /**
     * 删不到文件时静默返回，避免调用方为幂等再包一层。
     */
    @Override
    public void delete(String key) {
        Path target = resolveUnderRoot(ObjectKeys.normalize(key));
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warning("object store delete failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "object store delete failed");
        }
    }

    @Override
    public String storageType() {
        return "LOCAL";
    }

    /** 测试与启动日志用：规范化后的根。 */
    Path root() {
        return root;
    }

    private Path resolveUnderRoot(String normalizedKey) {
        Path resolved = root.resolve(normalizedKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "object key is invalid");
        }
        return resolved;
    }
}
