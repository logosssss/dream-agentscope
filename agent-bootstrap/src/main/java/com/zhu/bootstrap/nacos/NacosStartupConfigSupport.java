package com.zhu.bootstrap.nacos;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.InputStreamResource;

/**
 * 把 Nacos yaml 收成白名单属性源。不访问网络。
 */
public final class NacosStartupConfigSupport {

    private NacosStartupConfigSupport() {
    }

    /**
     * 启动拉业务 yaml：正文非空；白名单外键丢掉；允许过滤后为空。
     */
    public static MapPropertySource parseBusiness(String yaml) {
        return parse(
                yaml,
                NacosStartupConfig.BUSINESS_PROPERTY_SOURCE,
                NacosAllowlist.businessExact(),
                NacosAllowlist.businessPrefixes(),
                false,
                "nacos startup config");
    }

    /**
     * 热更业务 yaml：正文非空可解析；若展平后有键但无一进白名单则拒绝，避免误发布。
     */
    public static MapPropertySource parseBusinessRefresh(String yaml) {
        return parse(
                yaml,
                NacosStartupConfig.BUSINESS_PROPERTY_SOURCE,
                NacosAllowlist.businessExact(),
                NacosAllowlist.businessPrefixes(),
                true,
                "nacos refresh config");
    }

    /**
     * 启动拉 infra：正文非空；只留连接与密钥白名单。
     */
    public static MapPropertySource parseInfra(String yaml) {
        return parse(
                yaml,
                NacosStartupConfig.INFRA_PROPERTY_SOURCE,
                NacosAllowlist.infraExact(),
                Set.of(),
                false,
                "nacos infra config");
    }

    /**
     * 空白拒绝。requireAllowed 为 true 时：原文有键但过滤后为空则失败。
     */
    static MapPropertySource parse(
            String yaml,
            String sourceName,
            Set<String> allowedKeys,
            Set<String> allowedPrefixes,
            boolean requireAllowed,
            String label) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalStateException(label + " is blank");
        }
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> loaded = loader.load(
                    sourceName,
                    new InputStreamResource(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
            Map<String, Object> raw = new LinkedHashMap<>();
            for (PropertySource<?> source : loaded) {
                copyAll(source, raw);
            }
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (allowed(entry.getKey(), allowedKeys, allowedPrefixes) && entry.getValue() != null) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            if (requireAllowed && !raw.isEmpty() && filtered.isEmpty()) {
                throw new IllegalStateException(label + " has no allowed keys");
            }
            return new MapPropertySource(sourceName, filtered);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(label + " is invalid", ex);
        }
    }

    /** 键在精确白名单或命中前缀（含 list 下标）。 */
    static boolean allowed(String name, Set<String> allowedKeys, Set<String> allowedPrefixes) {
        if (name == null) {
            return false;
        }
        if (allowedKeys.contains(name)) {
            return true;
        }
        for (String prefix : allowedPrefixes) {
            if (name.equals(prefix)
                    || name.startsWith(prefix + ".")
                    || name.startsWith(prefix + "[")) {
                return true;
            }
        }
        return false;
    }

    private static void copyAll(PropertySource<?> source, Map<String, Object> target) {
        if (source instanceof OriginTrackedMapPropertySource tracked) {
            for (String name : tracked.getPropertyNames()) {
                target.put(name, tracked.getProperty(name));
            }
            return;
        }
        if (source instanceof MapPropertySource mapSource) {
            for (String name : mapSource.getPropertyNames()) {
                target.put(name, mapSource.getProperty(name));
            }
        }
    }
}
