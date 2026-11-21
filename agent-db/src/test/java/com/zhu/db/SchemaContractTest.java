package com.zhu.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * schema 契约测：Flyway 为唯一 DDL 源；共享表 Mapper 唯一 owner 为 agent-readmodel，其引用列必须 ⊆ 当前表结构。
 * 不连数据库；用仓库相对路径读脚本与 XML。
 */
class SchemaContractTest {

    /**
     * 多进程共用的表：Mapper 已收进 agent-readmodel，其它模块不得再持有同名 XML。
     * 表名 → 约定 Mapper 文件名（仅 BaseMapper 插入、XML 可为空）。
     */
    private static final Map<String, String> READMODEL_OWNED = Map.ofEntries(
            Map.entry("tenant", "TenantMapper.xml"),
            Map.entry("tenant_user", "TenantUserMapper.xml"),
            Map.entry("tenant_quota", "TenantQuotaMapper.xml"),
            Map.entry("quota_usage_log", "QuotaUsageLogMapper.xml"),
            Map.entry("login_credential", "LoginCredentialMapper.xml"),
            Map.entry("token_usage", "TokenUsageMapper.xml"),
            Map.entry("agent_event_audit", "AgentEventAuditMapper.xml"),
            Map.entry("safety_intervention", "SafetyInterventionMapper.xml"),
            Map.entry("model_config", "ModelConfigMapper.xml"),
            Map.entry("model_price", "ModelPriceMapper.xml"),
            Map.entry("attachment", "AttachmentMapper.xml"),
            Map.entry("attachment_ref", "AttachmentRefMapper.xml"),
            Map.entry("data_deletion_request", "DataDeletionRequestMapper.xml"),
            Map.entry("user_todo", "UserTodoMapper.xml"),
            Map.entry("user_feedback", "UserFeedbackMapper.xml"));

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE");

    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+`?([a-z0-9_]+)`?\\s+ADD\\s+(?:COLUMN\\s+)?`?([a-z0-9_]+)`?");

    private static final Pattern CHANGE_COLUMN = Pattern.compile(
            "(?is)CHANGE\\s+(?:COLUMN\\s+)?`?([a-z0-9_]+)`?\\s+`?([a-z0-9_]+)`?");

    private static final Pattern DROP_COLUMN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+`?([a-z0-9_]+)`?\\s+DROP\\s+(?:COLUMN\\s+)?`?([a-z0-9_]+)`?");

    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?is)DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?`?([a-z0-9_]+)`?");

    private static final Pattern XML_COLUMN = Pattern.compile("\\bcolumn\\s*=\\s*\"([a-zA-Z0-9_]+)\"");

    private static final Pattern FROM_TABLE = Pattern.compile(
            "(?is)\\b(?:FROM|INTO|UPDATE|JOIN)\\s+`?([a-z0-9_]+)`?");

    private static final Set<String> NON_COLUMN_LINE = Set.of(
            "primary", "unique", "key", "index", "constraint", "foreign", "fulltext", "spatial");

    /** 共享表的 Mapper 只允许在 agent-readmodel 存在；其 column= 须落在该表列集合里。 */
    @Test
    void readModelMapperColumnsMatchFlyway() throws IOException {
        Path root = repoRoot();
        Path migrationDir = root.resolve("agent-db/src/main/resources/db/migration");
        Map<String, Set<String>> schema = loadSchema(migrationDir);
        assertThat(schema.keySet()).as("shared tables must exist in Flyway").containsAll(READMODEL_OWNED.keySet());

        Path mapperDir = root.resolve("agent-readmodel/src/main/resources/mapper");
        assertThat(Files.isDirectory(mapperDir)).as("agent-readmodel mapper dir").isTrue();

        List<String> violations = new ArrayList<>();
        Set<Path> checkedMappers = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : READMODEL_OWNED.entrySet()) {
            String table = entry.getKey();
            assertThat(schema.get(table)).as("columns for " + table).isNotEmpty();
            Path dedicated = mapperDir.resolve(entry.getValue());
            if (!Files.isRegularFile(dedicated)) {
                violations.add("agent-readmodel missing " + entry.getValue() + " for " + table);
                continue;
            }
            checkedMappers.add(dedicated);
        }
        for (Path mapper : checkedMappers) {
            String body = stripXmlComments(Files.readString(mapper, StandardCharsets.UTF_8));
            Set<String> tables = tablesInMapper(body);
            tables.retainAll(schema.keySet());
            if (tables.isEmpty()) {
                // 空 XML（仅 BaseMapper）无列可校验。
                continue;
            }
            Set<String> allowed = new LinkedHashSet<>();
            for (String table : tables) {
                allowed.addAll(schema.get(table));
            }
            String relative = root.relativize(mapper).toString().replace('\\', '/');
            for (String column : columnsInMapper(body)) {
                if (!allowed.contains(column)) {
                    violations.add(relative + " uses column " + column + " not in tables " + tables);
                }
            }
        }
        assertThat(violations).as("schema drift").isEmpty();
    }

    /** 共享表只能有一个 owner：业务模块不得再留同名 Mapper XML，否则又会分叉。 */
    @Test
    void sharedTableMappersAreNotDuplicatedInBusinessModules() {
        Path root = repoRoot();
        List<String> duplicates = new ArrayList<>();
        for (String module : List.of("agent-access", "agent-admin", "agent-runtime")) {
            Path mapperDir = root.resolve(module).resolve("src/main/resources/mapper");
            if (!Files.isDirectory(mapperDir)) {
                continue;
            }
            for (String file : READMODEL_OWNED.values()) {
                if (Files.isRegularFile(mapperDir.resolve(file))) {
                    duplicates.add(module + "/src/main/resources/mapper/" + file);
                }
            }
        }
        assertThat(duplicates).as("shared table mappers must live only in agent-readmodel").isEmpty();
    }

    /** 仓库根：agent-db 模块目录的上一级。 */
    private static Path repoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "agent-db".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        Path nested = cwd.resolve("agent-db");
        if (Files.isDirectory(nested)) {
            return cwd;
        }
        throw new IllegalStateException("cannot locate repo root from " + cwd);
    }

    /**
     * 按迁移顺序推演表结构。只处理本仓库常见的 CREATE / ADD / CHANGE / DROP。
     */
    private static Map<String, Set<String>> loadSchema(Path migrationDir) throws IOException {
        Map<String, Set<String>> schema = new LinkedHashMap<>();
        List<Path> scripts;
        try (Stream<Path> stream = Files.list(migrationDir)) {
            scripts = stream
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        for (Path script : scripts) {
            String sql = Files.readString(script, StandardCharsets.UTF_8);
            applyDropTable(schema, sql);
            applyCreateTable(schema, sql);
            applyAddColumn(schema, sql);
            applyChangeColumn(schema, sql);
            applyDropColumn(schema, sql);
        }
        return schema;
    }

    private static void applyCreateTable(Map<String, Set<String>> schema, String sql) {
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            Set<String> columns = new LinkedHashSet<>();
            for (String rawLine : splitTopLevel(matcher.group(2))) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String first = firstToken(line);
                if (first.isEmpty() || NON_COLUMN_LINE.contains(first.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                columns.add(stripTicks(first).toLowerCase(Locale.ROOT));
            }
            schema.put(table, columns);
        }
    }

    /** 按顶层逗号切开列定义，忽略类型参数括号内的逗号。 */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static void applyAddColumn(Map<String, Set<String>> schema, String sql) {
        Matcher matcher = ADD_COLUMN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            String column = matcher.group(2).toLowerCase(Locale.ROOT);
            if (NON_COLUMN_LINE.contains(column)) {
                continue;
            }
            schema.computeIfAbsent(table, key -> new LinkedHashSet<>()).add(column);
        }
    }

    private static void applyChangeColumn(Map<String, Set<String>> schema, String sql) {
        Matcher matcher = CHANGE_COLUMN.matcher(sql);
        while (matcher.find()) {
            String oldName = matcher.group(1).toLowerCase(Locale.ROOT);
            String newName = matcher.group(2).toLowerCase(Locale.ROOT);
            for (Set<String> columns : schema.values()) {
                if (columns.remove(oldName)) {
                    columns.add(newName);
                }
            }
        }
    }

    private static void applyDropColumn(Map<String, Set<String>> schema, String sql) {
        Matcher matcher = DROP_COLUMN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            Set<String> columns = schema.get(table);
            if (columns != null) {
                columns.remove(matcher.group(2).toLowerCase(Locale.ROOT));
            }
        }
    }

    private static void applyDropTable(Map<String, Set<String>> schema, String sql) {
        Matcher matcher = DROP_TABLE.matcher(sql);
        while (matcher.find()) {
            schema.remove(matcher.group(1).toLowerCase(Locale.ROOT));
        }
    }

    private static Set<String> tablesInMapper(String body) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = FROM_TABLE.matcher(body);
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tables;
    }

    private static Set<String> columnsInMapper(String body) {
        Set<String> used = new LinkedHashSet<>();
        Matcher matcher = XML_COLUMN.matcher(body);
        while (matcher.find()) {
            used.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return used;
    }

    private static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    private static String firstToken(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || c == '(') {
                break;
            }
            i++;
        }
        return line.substring(start, i);
    }

    private static String stripTicks(String token) {
        if (token.length() >= 2 && token.startsWith("`") && token.endsWith("`")) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }
}
