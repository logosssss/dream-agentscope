package com.zhu.common.health;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.logging.Logger;

/**
 * Milvus 是否可达。这次只探测地址，不建集合，也不做检索。
 */
public final class MilvusProbe {

    private static final Logger log = Logger.getLogger(MilvusProbe.class.getName());

    /** 没写端口时用 Milvus 的 gRPC 端口。 */
    private static final int DEFAULT_PORT = 19530;

    private MilvusProbe() {
    }

    /**
     * 地址为空、解析不出主机，或在超时内连不上，都是不可达。
     * 不把异常原文打进响应，避免带上地址里的口令。
     *
     * @param uri            例如 http://host:19530，来自环境变量
     * @param timeoutMillis 连接超时。小于 1 时按 1 毫秒
     */
    public static boolean reachable(String uri, int timeoutMillis) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        int timeout = Math.max(timeoutMillis, 1);
        try {
            URI parsed = uri.contains("://") ? URI.create(uri.trim()) : URI.create("tcp://" + uri.trim());
            String host = parsed.getHost();
            int port = parsed.getPort() == -1 ? DEFAULT_PORT : parsed.getPort();
            if (host == null || host.isBlank()) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeout);
                return true;
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.warning("milvus is not reachable");
            return false;
        }
    }
}
