package com.zhu.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 校验本模块收录的 Flyway 脚本连续且唯一。不连数据库。
 */
class MigrationCatalogTest {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    /** V1～V43 须齐全、无缺口、无重复版本号。 */
    @Test
    void versionsAreContiguousThrough43() throws Exception {
        List<Integer> versions = listVersions();
        assertThat(versions).as("expected V1..V43").hasSize(43);
        for (int i = 1; i <= 43; i++) {
            assertThat(versions).as("missing V" + i).contains(i);
        }
    }

    private static List<Integer> listVersions() throws IOException, URISyntaxException {
        URL root = MigrationCatalogTest.class.getClassLoader().getResource("db/migration");
        assertThat(root).as("db/migration on classpath").isNotNull();
        Path dir = Paths.get(root.toURI());
        List<Integer> versions = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.sql")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                Matcher matcher = VERSION.matcher(name);
                assertThat(matcher.matches()).as("bad name: " + name).isTrue();
                versions.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return versions;
    }
}
