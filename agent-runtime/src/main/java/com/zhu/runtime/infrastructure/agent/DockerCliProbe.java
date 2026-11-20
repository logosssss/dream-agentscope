package com.zhu.runtime.infrastructure.agent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 运行层：探测本机 Docker CLI 是否可用。
 * {@code sandbox.enabled=true} 时由 companion 接线硬失败调用；不连 Docker daemon 以外的中间件。
 */
public final class DockerCliProbe {

    /** 探测命令；只读版本信息，不拉镜像、不起容器。 */
    private static final List<String> VERSION_COMMAND = List.of("docker", "version");

    /** 等待 CLI 退出的上限，避免探测卡住启动。 */
    private static final long TIMEOUT_SECONDS = 15L;

    private DockerCliProbe() {
    }

    /**
     * 可替换的进程执行口。单测注入假实现，避免碰真 Docker。
     */
    @FunctionalInterface
    public interface ProcessRunner {

        /**
         * 执行命令；返回退出码，stderr/stdout 可写入 {@code detail}。
         */
        int run(List<String> command, StringBuilder detail) throws Exception;
    }

    /** 用本机 {@code docker version} 探测；失败抛 {@link IllegalStateException}。 */
    public static void ensureAvailable() {
        ensureAvailable(DockerCliProbe::runDockerVersion);
    }

    /**
     * 可注入 runner 的探测。退出码非 0 或启动失败都视为 Docker 不可用。
     */
    public static void ensureAvailable(ProcessRunner runner) {
        if (runner == null) {
            throw new IllegalStateException(
                    "sandbox.enabled=true but Docker probe runner is null; refusing Remote fallback");
        }
        StringBuilder detail = new StringBuilder();
        try {
            int code = runner.run(VERSION_COMMAND, detail);
            if (code != 0) {
                throw new IllegalStateException(
                        "sandbox.enabled=true but docker unavailable: exit="
                                + code
                                + detailSuffix(detail));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "sandbox.enabled=true but docker unavailable: " + ex.getMessage(), ex);
        }
    }

    /** 真实进程：{@code docker version}，超时或 IO 失败向上抛。 */
    static int runDockerVersion(List<String> command, StringBuilder detail) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("docker version timed out after " + TIMEOUT_SECONDS + "s");
        }
        if (detail != null && output != null && !output.isBlank()) {
            String trimmed = output.trim();
            if (trimmed.length() > 500) {
                trimmed = trimmed.substring(0, 500) + "...";
            }
            detail.append(" detail=").append(trimmed);
        }
        return process.exitValue();
    }

    private static String detailSuffix(StringBuilder detail) {
        if (detail == null || detail.isEmpty()) {
            return "";
        }
        return detail.toString();
    }
}
