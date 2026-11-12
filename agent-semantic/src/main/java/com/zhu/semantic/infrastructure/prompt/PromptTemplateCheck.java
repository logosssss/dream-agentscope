package com.zhu.semantic.infrastructure.prompt;

import com.zhu.semantic.api.prompt.PlatformPrompt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.boot.json.JsonParseException;
import org.springframework.boot.json.JsonParserFactory;

/**
 * 仓库里的提示词模板校验。测试调用，不连接 Nacos，也不发布。
 * 占位符必须是代码里登记过的 {{name}}。标成 JSON 的模板在替换后必须能解析。
 */
public final class PromptTemplateCheck {

    /** prompt.agent.purpose，后面最多再加一段 variant。 */
    private static final Pattern KEY = Pattern.compile("^prompt(?:\\.[a-z][a-z0-9]*(?:-[a-z0-9]+)*){2,3}$");

    /** 变量名和代码 Map 的键一致。空格和引号不算合法变量。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private PromptTemplateCheck() {
    }

    /**
     * 校验一条模板。allowed 为 null 表示代码里没有这条键的变量约定。
     * json 为真时，把每个占位符换成 sample 再解析。单括号留给正文，不当成占位符。
     */
    public static void check(String key, String body, Set<String> allowed, boolean json) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("prompt key is invalid");
        }
        if (allowed == null) {
            throw new IllegalArgumentException("prompt key has no variable contract");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("prompt body is blank");
        }
        String rendered = placeholders(body, allowed);
        if (!json) {
            return;
        }
        parseJson(rendered);
    }

    /**
     * 用代码里登记的变量约定校验。没有登记的键直接拒绝，避免模板写出调用方填不上的变量。
     */
    public static void checkRegistered(String key, String body, boolean json) {
        check(key, body, PlatformPrompt.allowedVariables(key), json);
    }

    /**
     * 校验目录里的一层模板。目录不存在就跳过。只认 .md 和 .json，.json 要能解析。
     * 读失败当成模板不合法，不把路径以外的内容写进异常。
     */
    public static void checkDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String filename = file.getFileName().toString();
                boolean json = filename.endsWith(".json");
                String key = keyOf(filename, json);
                String body = Files.readString(file, StandardCharsets.UTF_8);
                checkRegistered(key, body, json);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("prompt file is invalid");
        }
    }

    /** 发布和校验共用。文件名必须是键再加一种后缀。 */
    static String fileKey(String filename) {
        boolean json = filename.endsWith(".json");
        return keyOf(filename, json);
    }

    /** 文件名必须是键再加一种后缀。其它后缀拒绝，避免漏检。 */
    private static String keyOf(String filename, boolean json) {
        if (json) {
            return filename.substring(0, filename.length() - ".json".length());
        }
        if (filename.endsWith(".md")) {
            return filename.substring(0, filename.length() - ".md".length());
        }
        throw new IllegalArgumentException("prompt file is invalid");
    }

    /**
     * 扫完 {{name}}。没闭合、名字不合法，或不在 allowed 里，都拒绝。
     * 返回把占位符换成 sample 之后的正文，供 JSON 解析。
     */
    private static String placeholders(String body, Set<String> allowed) {
        StringBuilder rendered = new StringBuilder();
        int cursor = 0;
        while (cursor < body.length()) {
            int open = body.indexOf("{{", cursor);
            int close = body.indexOf("}}", cursor);
            if (open < 0 && close < 0) {
                break;
            }
            if (open < 0 || (close >= 0 && close < open)) {
                throw new IllegalArgumentException("prompt placeholder is incomplete");
            }
            int end = body.indexOf("}}", open + 2);
            if (end < 0) {
                throw new IllegalArgumentException("prompt placeholder is incomplete");
            }
            String name = body.substring(open + 2, end).trim();
            if (!NAME.matcher(name).matches() || !allowed.contains(name)) {
                if (!NAME.matcher(name).matches()) {
                    throw new IllegalArgumentException("prompt placeholder is incomplete");
                }
                throw new IllegalArgumentException("prompt variable is unknown");
            }
            rendered.append(body, cursor, open).append("sample");
            cursor = end + 2;
        }
        rendered.append(body, cursor, body.length());
        return rendered.toString();
    }

    /** 对象或数组都能过。解析失败不带上正文，避免把话术打进日志。 */
    private static void parseJson(String rendered) {
        String trimmed = rendered.stripLeading();
        try {
            if (trimmed.startsWith("[")) {
                JsonParserFactory.getJsonParser().parseList(rendered);
                return;
            }
            JsonParserFactory.getJsonParser().parseMap(rendered);
        } catch (JsonParseException ex) {
            throw new IllegalArgumentException("prompt json is invalid");
        }
    }
}
