package com.zhu.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * 把「这台机器上 Testcontainers 到底看到了什么」打到标准输出，由 CI 的护栏步骤读出来。
 *
 * <p>起因：GitHub 的 ubuntu-latest 上 `docker run hello-world` 是成功的，但四条
 * {@code @Testcontainers(disabledWithoutDocker = true)} 的端到端仍被判「Docker 不可用」。
 * 只有 shell 的结论不够，需要 JVM 里 Testcontainers 的原始判断过程。这里永远不失败——
 * 它只负责留下诊断信息，拦截交给 {@code 端到端用例必须真跑} 那一步。
 */
class DockerProbeTest {

    @Test
    void printsWhatTestcontainersSees() {
        enableTestcontainersDebug();
        System.out.println("probe.user.home=" + System.getProperty("user.home"));
        System.out.println("probe.docker.host=" + env("DOCKER_HOST"));
        System.out.println("probe.testcontainers.host=" + env("TESTCONTAINERS_HOST_OVERRIDE"));
        System.out.println("probe.docker.config=" + env("DOCKER_CONFIG"));
        Path socket = Path.of("/var/run/docker.sock");
        System.out.println("probe.socket.exists=" + Files.exists(socket));
        System.out.println("probe.socket.readable=" + Files.isReadable(socket));
        System.out.println("probe.socket.writable=" + Files.isWritable(socket));
        try {
            boolean available = DockerClientFactory.instance().isDockerAvailable();
            System.out.println("probe.available=" + available);
        } catch (Throwable error) {
            System.out.println("probe.available=false");
            printError("probe.isDockerAvailable", error);
        }
        // 再走一次 client()：可用时拿服务端版本，不可用时把 IllegalStateException 的文字与成因打出来。
        try {
            var client = DockerClientFactory.instance().client();
            System.out.println("probe.client=ok");
            System.out.println("probe.server.version=" + client.versionCmd().exec().getVersion());
        } catch (Throwable error) {
            System.out.println("probe.client=failed");
            printError("probe.client", error);
        }
    }

    private static void printError(String label, Throwable error) {
        System.out.println(label + ".error=" + error);
        Throwable cause = error.getCause();
        int depth = 0;
        while (cause != null && depth++ < 5) {
            System.out.println(label + ".cause=" + cause);
            cause = cause.getCause();
        }
    }

    /**
     * Testcontainers 的策略失败原因大多打在 DEBUG 上；把那一支日志打开，surefire 的
     * system-out 就能带出来（没有 logback 时安静跳过，用反射是为了不给测试加编译期依赖）。
     */
    private static void enableTestcontainersDebug() {
        try {
            Class<?> level = Class.forName("ch.qos.logback.classic.Level");
            Object debug = level.getField("DEBUG").get(null);
            Class<?> contextClass = Class.forName("ch.qos.logback.classic.LoggerContext");
            Object context = Class.forName("org.slf4j.LoggerFactory")
                    .getMethod("getILoggerFactory")
                    .invoke(null);
            if (!contextClass.isInstance(context)) {
                System.out.println("probe.debug=not-logback");
                return;
            }
            Object logger = contextClass.getMethod("getLogger", String.class).invoke(context, "org.testcontainers");
            logger.getClass().getMethod("setLevel", level).invoke(logger, debug);
            System.out.println("probe.debug=enabled");
        } catch (Throwable ignored) {
            System.out.println("probe.debug=unavailable");
        }
    }

    /** 环境变量可能不存在，统一给一个可读的占位。 */
    private static String env(String name) {
        Map<String, String> environment = System.getenv();
        String value = environment.get(name);
        return value == null || value.isBlank() ? "<unset>" : value;
    }
}
