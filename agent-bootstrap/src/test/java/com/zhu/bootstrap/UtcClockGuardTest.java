package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 业务代码只许通过 {@code UtcTimes} 读墙钟，仓储里的 DATETIME 一律按 UTC 写。
 *
 * <p>为什么要守：同一张库里曾经三种口径并存——代码写 UTC、代码写服务器本地、
 * 列默认值跟会话时区。新增的模型单价按本地时间落库、取价按 UTC 比较，东八区下八小时内
 * 取不到价、费用记 0；保留策略和用量报表的窗口也跟着偏。这种错没有异常、没有红灯，
 * 只有对账时才看得出来，所以要靠这条静态检查拦住下一次「顺手写个 now()」。
 *
 * <p>例外只有 {@link com.zhu.common.util.UtcTimes} 自身：它是唯一入口。
 * 测试代码不受限（固定时间比读墙钟更好，但那是另一件事）。
 */
class UtcClockGuardTest {

    /** 直接读墙钟的写法。构造 Clock 的地方不受限（那个本来就是可注入的时计）。 */
    private static final Pattern WALL_CLOCK = Pattern.compile(
            "\\b(?:LocalDateTime|LocalDate|LocalTime|OffsetDateTime|Instant)\\.now\\s*\\(");

    /** 默认时区取值同样会让落库口径跟着机器走。 */
    private static final Pattern DEFAULT_ZONE = Pattern.compile("ZoneId\\.systemDefault\\s*\\(");

    /**
     * 允许读墙钟 / 取默认时区的两个文件，各有明确职责：
     * <ul>
     *   <li>{@code UtcTimes}：全仓唯一的墙钟入口。</li>
     *   <li>{@code ScheduleTimes}：cron 时区名空白时的进程默认兜底只允许在这里出现一次；
     *       别处要时区就传 {@code ScheduleTimes.zone(...)} 的结果。</li>
     * </ul>
     */
    private static final String UTC_TIMES = "agent-common/src/main/java/com/zhu/common/util/UtcTimes.java";

    private static final String SCHEDULE_TIMES =
            "agent-ops/src/main/java/com/zhu/ops/schedule/ScheduleTimes.java";

    @Test
    void mainCodeReadsTheClockOnlyThroughUtcTimes() throws IOException {
        Path root = repoRoot();
        List<Path> sources;
        try (Stream<Path> stream = Files.walk(root)) {
            sources = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .collect(Collectors.toList());
        }
        assertThat(sources).as("main sources must be visible from " + root).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String relative = root.relativize(source).toString().replace('\\', '/');
            if (UTC_TIMES.equals(relative) || SCHEDULE_TIMES.equals(relative)) {
                continue;
            }
            String body = Files.readString(source, StandardCharsets.UTF_8);
            violations.addAll(hits(relative, body, WALL_CLOCK, "读墙钟"));
            violations.addAll(hits(relative, body, DEFAULT_ZONE, "取默认时区"));
        }
        assertThat(violations)
                .as("业务代码请改用 UtcTimes.now() / UtcTimes.instant()")
                .isEmpty();
    }

    /** 命中行连行号一起报，免得只看到一个文件名。 */
    private static List<String> hits(String relative, String body, Pattern pattern, String what) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            int line = 1;
            for (int i = 0; i < matcher.start(); i++) {
                if (body.charAt(i) == '\n') {
                    line++;
                }
            }
            found.add(relative + ":" + line + " " + what + " " + matcher.group());
        }
        return found;
    }

    /** 仓库根：agent-bootstrap 模块目录的上一级。 */
    private static Path repoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "agent-bootstrap".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        Path nested = cwd.resolve("agent-bootstrap");
        if (Files.isDirectory(nested)) {
            return cwd;
        }
        throw new IllegalStateException("cannot locate repo root from " + cwd);
    }
}
