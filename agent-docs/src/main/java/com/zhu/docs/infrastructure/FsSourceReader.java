package com.zhu.docs.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 按 include / exclude 列出一个仓库根下的文件。只返回相对路径，不读内容。
 * 白名单为空时什么都不收，避免把整个磁盘扫进索引。
 */
public final class FsSourceReader {

    /**
     * 列出命中白名单、且没有被排除的普通文件。
     * 根目录不存在或不是目录时直接失败，空列表会把配错路径伪装成「没有文档」。
     * 排除优先于包含。相对路径统一用 {@code /}。
     *
     * @param root    仓库根
     * @param include 命中任意一条即候选
     * @param exclude 命中任意一条即丢掉
     */
    public List<SourceFile> list(Path root, List<String> include, List<String> exclude) {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("docs source root is not a directory: " + root);
        }
        List<String> includes = include == null ? List.of() : include;
        List<String> excludes = exclude == null ? List.of() : exclude;
        if (includes.isEmpty()) {
            return List.of();
        }
        List<SourceFile> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> consider(root, file, includes, excludes, found));
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to list docs source: " + root, ex);
        }
        found.sort(Comparator.comparing(SourceFile::relativePath));
        return List.copyOf(found);
    }

    private static void consider(
            Path root, Path file, List<String> includes, List<String> excludes, List<SourceFile> found) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.isBlank() || !anyMatch(includes, relative) || anyMatch(excludes, relative)) {
            return;
        }
        found.add(new SourceFile(relative, file.toAbsolutePath().normalize()));
    }

    private static boolean anyMatch(List<String> patterns, String relative) {
        for (String pattern : patterns) {
            if (PathGlob.matches(pattern, relative)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一篇待读的源文件。
     *
     * @param relativePath 相对仓库根，分隔符是 {@code /}
     * @param absolutePath 本机绝对路径，给调用方读 UTF-8
     */
    public record SourceFile(String relativePath, Path absolutePath) {
    }
}
