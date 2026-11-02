package com.zhu.admin.nacos;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.InputStreamResource;

/**
 * 把 Nacos 拉下来的 yaml 收成白名单属性源。不访问网络。
 */
public final class NacosAdminStartupConfigSupport {

    private NacosAdminStartupConfigSupport() {
    }

    /**
     * 空白正文拒绝。解析后只保留允许的键，其它键静默丢掉。
     * 允许的键一个都没有时仍返回空属性源，表示 Nacos 可达但没覆盖任何旋钮。
     */
    public static MapPropertySource parse(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalStateException("nacos admin startup config is blank");
        }
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> loaded = loader.load(
                    NacosAdminStartupConfig.PROPERTY_SOURCE,
                    new InputStreamResource(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (PropertySource<?> source : loaded) {
                copyAllowed(source, filtered);
            }
            return new MapPropertySource(NacosAdminStartupConfig.PROPERTY_SOURCE, filtered);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("nacos admin startup config is invalid", ex);
        }
    }

    /** 只拷贝白名单键。嵌套 map 由 Yaml 加载器展平后再进来。 */
    private static void copyAllowed(PropertySource<?> source, Map<String, Object> filtered) {
        if (!(source instanceof OriginTrackedMapPropertySource tracked)) {
            if (source instanceof MapPropertySource mapSource) {
                for (String name : mapSource.getPropertyNames()) {
                    putIfAllowed(filtered, name, mapSource.getProperty(name));
                }
            }
            return;
        }
        for (String name : tracked.getPropertyNames()) {
            putIfAllowed(filtered, name, tracked.getProperty(name));
        }
    }

    private static void putIfAllowed(Map<String, Object> filtered, String name, Object value) {
        if (name != null && NacosAdminStartupConfig.ALLOWED_KEYS.contains(name) && value != null) {
            filtered.put(name, value);
        }
    }
}
