package com.zhu.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 删除执行器碰到的表必须在 Flyway 里还活着。
 *
 * <p>为什么需要它：审批表在 V40 硬删、情景记忆与画像表在 V41 硬删，而
 * {@code UserDataPurger} 是之后写的，仍照着旧清单删。于是 {@code dataType=ALL} 的删除请求
 * 在数第一张幽灵表的行数时就抛 SQL 错误：前面的会话与附件已经删了，后面的知识库、待办、
 * 登录凭据与整段脱敏全部跳过，请求落 FAILED，重试继续半删。dry-run 也一样，它也要先数行。
 *
 * <p>这类错只在真连库跑一次时才暴露，而那条端到端要 Docker。这里改成不连库：直接读源码里的
 * SQL 字面量与脱敏清单，跟迁移推演出的表集合对照，秒级、无依赖。
 *
 * <p>表名搬家或加新表时要同步改这里指向的路径。
 */
class PurgeTableReferenceTest {

    /** 被检查的执行器。表名只从它的 SQL 字面量和脱敏清单里取。 */
    private static final Path PURGER = Path.of(
            "agent-access/src/main/java/com/zhu/access/application/lifecycle/UserDataPurger.java");

    /**
     * SQL 里的表名只出现在这些关键字后面。拼接列名的 {@code "FROM " + table} 因为后面紧跟引号，
     * 不会被当成表名，脱敏清单另走 {@link #ANONYMIZE_BLOCK}。
     */
    private static final Pattern TABLE_IN_SQL = Pattern.compile(
            "(?is)\\b(?:FROM|INTO|UPDATE|JOIN)\\s+([a-z][a-z0-9_]{2,})");

    /** 脱敏清单是 {@code List.of(...)} 常量，SQL 里看不到具体表名。 */
    private static final Pattern ANONYMIZE_BLOCK = Pattern.compile(
            "(?is)ANONYMIZE_TABLES\\s*=\\s*List\\.of\\((.*?)\\)\\s*;");

    private static final Pattern QUOTED = Pattern.compile("\"([a-z][a-z0-9_]{2,})\"");

    /** 源码引用的每张表都要能在迁移推演出的表集合里找到。 */
    @Test
    void everyTableThePurgerTouchesStillExists() throws IOException {
        Path root = SchemaContractTest.repoRoot();
        Map<String, Set<String>> schema = SchemaContractTest.loadSchema(
                root.resolve("agent-db/src/main/resources/db/migration"));
        assertThat(schema).as("migrations must yield tables").isNotEmpty();

        Set<String> referenced = referencedTables(
                Files.readString(root.resolve(PURGER), StandardCharsets.UTF_8));
        assertThat(referenced).as("purger must reference tables").isNotEmpty();

        Set<String> missing = new LinkedHashSet<>(referenced);
        missing.removeAll(schema.keySet());
        assertThat(missing)
                .as("UserDataPurger 还在删/脱敏已不存在的表，删除请求会中途失败")
                .isEmpty();
    }

    /** SQL 字面量里的表名，加上脱敏清单里的表名。 */
    private static Set<String> referencedTables(String source) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher inSql = TABLE_IN_SQL.matcher(source);
        while (inSql.find()) {
            tables.add(inSql.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher block = ANONYMIZE_BLOCK.matcher(source);
        while (block.find()) {
            Matcher quoted = QUOTED.matcher(block.group(1));
            while (quoted.find()) {
                tables.add(quoted.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }
}
