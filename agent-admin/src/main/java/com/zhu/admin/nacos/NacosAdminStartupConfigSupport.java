package com.zhu.admin.nacos;

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
 * 把管理进程 Nacos yaml 收成白名单属性源。不访问网络。
 */
public final class NacosAdminStartupConfigSupport {

    private NacosAdminStartupConfigSupport() {
    }

    /**
     * 业务 yaml：正文非空；白名单外键丢掉。白名单为空时得到空属性源。
     */
    public static MapPropertySource parseBusiness(String yaml) {
        return parse(
                yaml,
                NacosAdminStartupConfig.BUSINESS_PROPERTY_SOURCE,
                NacosAdminAllowlist.businessExact(),
                NacosAdminAllowlist.businessPrefixes(),
                "nacos admin startup config");
    }

    /**
     * infra yaml：正文非空；只留连接与管理令牌。
     */
    public static MapPropertySource parseInfra(String yaml) {
        return parse(
                yaml,
                NacosAdminStartupConfig.INFRA_PROPERTY_SOURCE,
                NacosAdminAllowlist.infraExact(),
                Set.of(),
                "nacos admin infra config");
    }

    private static MapPropertySource parse(
            String yaml,
            String sourceName,
            Set<String> allowedKeys,
            Set<String> allowedPrefixes,
            String label) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalStateException(label + " is blank");
        }
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> loaded = loader.load(
                    sourceName,
                    new InputStreamResource(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (PropertySource<?> source : loaded) {
                copyAllowed(source, filtered, allowedKeys, allowedPrefixes);
            }
            return new MapPropertySource(sourceName, filtered);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(label + " is invalid", ex);
        }
    }

    private static void copyAllowed(
            PropertySource<?> source,
            Map<String, Object> filtered,
            Set<String> allowedKeys,
            Set<String> allowedPrefixes) {
        if (source instanceof OriginTrackedMapPropertySource tracked) {
            for (String name : tracked.getPropertyNames()) {
                putIfAllowed(filtered, name, tracked.getProperty(name), allowedKeys, allowedPrefixes);
            }
            return;
        }
        if (source instanceof MapPropertySource mapSource) {
            for (String name : mapSource.getPropertyNames()) {
                putIfAllowed(filtered, name, mapSource.getProperty(name), allowedKeys, allowedPrefixes);
            }
        }
    }

    private static void putIfAllowed(
            Map<String, Object> filtered,
            String name,
            Object value,
            Set<String> allowedKeys,
            Set<String> allowedPrefixes) {
        if (name == null || value == null) {
            return;
        }
        if (allowedKeys.contains(name)) {
            filtered.put(name, value);
            return;
        }
        for (String prefix : allowedPrefixes) {
            if (name.equals(prefix)
                    || name.startsWith(prefix + ".")
                    || name.startsWith(prefix + "[")) {
                filtered.put(name, value);
                return;
            }
        }
    }
}
