package com.zhu.admin.nacos;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * 从 classpath 的 nacos-allowlist.yml 加载管理进程白名单。
 */
public final class NacosAdminAllowlist {

    private static final String RESOURCE = "nacos-allowlist.yml";

    private static final NacosAdminAllowlist INSTANCE = load();

    private final Set<String> businessExact;

    private final Set<String> businessPrefixes;

    private final Set<String> infraExact;

    private NacosAdminAllowlist(Set<String> businessExact, Set<String> businessPrefixes, Set<String> infraExact) {
        this.businessExact = businessExact;
        this.businessPrefixes = businessPrefixes;
        this.infraExact = infraExact;
    }

    /** 业务精确键。当前可为 empty。 */
    public static Set<String> businessExact() {
        return INSTANCE.businessExact;
    }

    /** 业务前缀。 */
    public static Set<String> businessPrefixes() {
        return INSTANCE.businessPrefixes;
    }

    /** infra 精确键。 */
    public static Set<String> infraExact() {
        return INSTANCE.infraExact;
    }

    @SuppressWarnings("unchecked")
    private static NacosAdminAllowlist load() {
        InputStream in = NacosAdminAllowlist.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException(RESOURCE + " is missing on classpath");
        }
        try (InputStream stream = in) {
            Object root = new Yaml().load(stream);
            if (!(root instanceof Map<?, ?> map)) {
                throw new IllegalStateException(RESOURCE + " root must be a map");
            }
            Map<String, Object> business = section(map, "business");
            Map<String, Object> infra = section(map, "infra");
            return new NacosAdminAllowlist(
                    Set.copyOf(stringList(business.get("exact"))),
                    Set.copyOf(stringList(business.get("prefixes"))),
                    Set.copyOf(stringList(infra.get("exact"))));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(RESOURCE + " is invalid", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<?, ?> root, String name) {
        Object value = root.get(name);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException(RESOURCE + " missing section: " + name);
        }
        return (Map<String, Object>) map;
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(RESOURCE + " list fields must be sequences");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return List.copyOf(out);
    }
}
