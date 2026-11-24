package com.zhu.docs.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 白名单列文件。排除优先，空 include 不扫描。 */
class FsSourceReaderTest {

    private final FsSourceReader reader = new FsSourceReader();

    /** `agent-&#42;/README.md` 只匹配一层。嵌套模块、target、非 md 都不收。 */
    @Test
    void includeAndExclude(@TempDir Path root) throws Exception {
        write(root, "docs/a.md");
        write(root, "docs/b.txt");
        write(root, "README.md");
        write(root, "agent-access/README.md");
        write(root, "agent-cache/agent-cache-api/README.md");
        write(root, "target/skip.md");
        write(root, "node_modules/skip.md");

        List<String> relative = reader.list(
                        root,
                        List.of("docs/**/*.md", "README.md", "agent-*/README.md"),
                        List.of("**/target/**", "**/node_modules/**"))
                .stream()
                .map(FsSourceReader.SourceFile::relativePath)
                .toList();

        assertThat(relative).containsExactly("README.md", "agent-access/README.md", "docs/a.md");
    }

    /** 没给 include 时返回空，而不是把根目录全收进来。 */
    @Test
    void emptyIncludeReadsNothing(@TempDir Path root) throws Exception {
        write(root, "README.md");
        assertThat(reader.list(root, List.of(), List.of())).isEmpty();
    }

    /** 根不是目录就失败，避免把配错的路径当成没有文档。 */
    @Test
    void missingRootFails(@TempDir Path root) {
        assertThatThrownBy(() -> reader.list(root.resolve("missing"), List.of("**/*.md"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void write(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent() == null ? root : file.getParent());
        Files.writeString(file, "# t\n");
    }
}
