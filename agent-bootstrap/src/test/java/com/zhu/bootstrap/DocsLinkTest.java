package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 文档链接检查：仓库内的相对链接必须指向真实存在的文件。
 *
 * <p>为什么要测：文档搬家（例如 {@code doc/} 并入 {@code docs/reference/}）和模块改名时，
 * 链接是最容易悄悄烂掉的部分——人不会点开每一篇，CI 可以。
 */
class DocsLinkTest {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");

    /** 只检查仓库内的相对链接；外链的可用性不归单元测试管。 */
    @Test
    void relativeLinksResolve() throws IOException {
        Path root = repositoryRoot();
        List<String> broken = new ArrayList<>();
        for (Path document : markdownDocuments(root)) {
            String text = Files.readString(document, StandardCharsets.UTF_8);
            Matcher matcher = MARKDOWN_LINK.matcher(text);
            while (matcher.find()) {
                String target = matcher.group(1).trim();
                if (isExternal(target)) {
                    continue;
                }
                String path = target.split("#", 2)[0];
                if (path.isBlank() || path.startsWith("/") || isIllustrative(path)) {
                    continue;
                }
                Path resolved;
                try {
                    resolved = document.getParent().resolve(path).normalize();
                } catch (java.nio.file.InvalidPathException ignored) {
                    // 不是路径（文档里的示意写法），交给 isIllustrative 的同一条判断逻辑跳过
                    continue;
                }
                if (!Files.exists(resolved)) {
                    broken.add(root.relativize(document) + " -> " + target);
                }
            }
        }
        assertThat(broken)
                .as("broken relative links (fix the link or the target file)")
                .isEmpty();
    }

    private static boolean isExternal(String target) {
        return target.startsWith("http://")
                || target.startsWith("https://")
                || target.startsWith("mailto:")
                || target.startsWith("#");
    }

    /**
     * 占位/示例写法不算链接：教程里会出现 {@code assets/fig-0N-*.png} 这类描述文件命名规律的文本，
     * 通配符与尖括号都不可能是真实路径。
     */
    private static boolean isIllustrative(String path) {
        return path.contains("*")
                || path.contains("?")
                || path.contains("{")
                || path.contains("<")
                || path.contains("$");
    }

    /**
     * 检查范围：根 README、docs 下全部 Markdown、以及每个模块目录的 README。
     * tutorial 里的 html 与图片不扫（它们是成品，不是导航）。
     */
    private static Set<Path> markdownDocuments(Path root) throws IOException {
        Set<Path> documents = new LinkedHashSet<>();
        Path readme = root.resolve("README.md");
        if (Files.exists(readme)) {
            documents.add(readme);
        }
        try (Stream<Path> docs = Files.walk(root.resolve("docs"))) {
            docs.filter(path -> path.toString().endsWith(".md"))
                    .filter(Files::isRegularFile)
                    .forEach(documents::add);
        }
        try (Stream<Path> modules = Files.list(root)) {
            modules.filter(Files::isDirectory)
                    .map(directory -> directory.resolve("README.md"))
                    .filter(Files::isRegularFile)
                    .forEach(documents::add);
        }
        return documents;
    }

    /** 从当前目录向上找带 {@code <modules>} 的 pom.xml，兼容 Maven 从任意模块启动。 */
    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                try {
                    if (Files.readString(pom, StandardCharsets.UTF_8).contains("<modules>")) {
                        return current;
                    }
                } catch (IOException ignored) {
                    // 继续向上一层找
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root with <modules> pom.xml not found");
    }
}
