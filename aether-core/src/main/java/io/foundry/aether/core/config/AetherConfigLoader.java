/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads {@link AetherConfig} from multiple sources with a defined priority
 * chain.
 *
 * <p>
 * Priority order (highest wins):
 * <ol>
 * <li>Environment variables ({@code AETHER_PROVIDER_*},
 * {@code AETHER_SERVICE_*})
 * <li>{@code ./aether.yml} or {@code ./aether.yaml} in the working directory
 * <li>{@code ~/.aether/config.yml} user-level defaults
 * </ol>
 *
 * <p>
 * Provider type discovery is delegated to {@link ProviderFactory}
 * implementations found via {@link ServiceLoader}.
 */
public final class AetherConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(AetherConfigLoader.class);

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)(?::-(.*?))?\\}");

    private static final String PROVIDER_ENV_PREFIX = "AETHER_PROVIDER_";
    private static final String SERVICE_ENV_PREFIX = "AETHER_SERVICE_";

    private AetherConfigLoader() {
    }

    /**
     * Loads config using the full priority chain: env vars override project YAML
     * which overrides user YAML.
     */
    public static AetherConfig load() {
        return load(System.getenv());
    }

    static AetherConfig load(Map<String, String> env) {
        Map<String, ProviderFactory> factories = _loadProviderFactories();
        AetherConfig result = AetherConfig.builder().build(factories);

        // 3. User-level config (lowest priority)
        Path userConfig = Path.of(System.getProperty("user.home"), ".aether", "config.yml");
        if (Files.exists(userConfig)) {
            result = _merge(result, _fromFile(userConfig, env, factories));
        }

        // 2. Project-level YAML
        for (String name : new String[]{"aether.yml", "aether.yaml"}) {
            Path projectConfig = Path.of(name);
            if (Files.exists(projectConfig)) {
                result = _merge(result, _fromFile(projectConfig, env, factories));
                break;
            }
        }

        // 1. Env vars (highest priority)
        result = _merge(result, _fromEnv(env, factories));

        return result;
    }

    /** Load from a specific YAML file. Supports {@code ${VAR}} interpolation. */
    public static AetherConfig fromFile(Path yamlPath) {
        Map<String, ProviderFactory> factories = _loadProviderFactories();
        return _fromFile(yamlPath, System.getenv(), factories);
    }

    /** Load from environment variables only. */
    public static AetherConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Load from a custom env map (useful in tests). */
    public static AetherConfig fromEnvironment(Map<String, String> env) {
        return _fromEnv(env, _loadProviderFactories());
    }

    // --- Internal ---

    private static AetherConfig _fromFile(Path path, Map<String, String> env, Map<String, ProviderFactory> factories) {
        try {
            String raw = Files.readString(path);
            String interpolated = _interpolate(raw, env);
            return _parseYaml(interpolated, factories);
        } catch (IOException e) {
            throw new InvalidConfigurationException("aether", "config.load", "Failed to read config file: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static AetherConfig _parseYaml(String content, Map<String, ProviderFactory> factories) {
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(content, Map.class);
            Map<String, Object> aetherSection = (Map<String, Object>) root.getOrDefault("aether", Map.of());

            AetherConfig.Builder builder = AetherConfig.builder();

            // Parse providers
            Map<String, Object> providersSection = (Map<String, Object>) aetherSection.getOrDefault("providers",
                    Map.of());
            for (Map.Entry<String, Object> entry : providersSection.entrySet()) {
                String alias = entry.getKey();
                Map<String, Object> providerBlock = (Map<String, Object>) entry.getValue();
                String type = String.valueOf(providerBlock.get("type"));
                ProviderFactory factory = factories.get(type);
                if (factory == null) {
                    throw new InvalidConfigurationException("aether", "config.load", "Unknown provider type '" + type
                            + "' for provider '" + alias + "'. Available types: " + factories.keySet());
                }
                Map<String, String> props = _toStringMap(providerBlock, "type");
                builder.provider(alias, factory.createConfig(alias, props));
            }

            // Parse service routing — store as-is, validate lazily on createService()
            Map<String, Object> servicesSection = (Map<String, Object>) aetherSection.getOrDefault("services",
                    Map.of());
            for (Map.Entry<String, Object> entry : servicesSection.entrySet()) {
                String serviceKey = entry.getKey();
                if (!ServiceTypes.REGISTRY.containsKey(serviceKey)) {
                    log.warn("Unknown service key '{}' in config, skipping", serviceKey);
                    continue;
                }
                builder.routeByKey(serviceKey, String.valueOf(entry.getValue()));
            }

            return builder.build(factories);
        } catch (IOException e) {
            throw new InvalidConfigurationException("aether", "config.load", "Failed to parse YAML config", e);
        }
    }

    private static AetherConfig _fromEnv(Map<String, String> env, Map<String, ProviderFactory> factories) {
        AetherConfig.Builder builder = AetherConfig.builder();

        // Discover provider aliases by scanning for AETHER_PROVIDER_*_TYPE
        Map<String, Map<String, String>> providerProps = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PROVIDER_ENV_PREFIX) && key.endsWith("_TYPE")) {
                String aliasUpper = key.substring(PROVIDER_ENV_PREFIX.length(), key.length() - "_TYPE".length());
                providerProps.computeIfAbsent(aliasUpper, k -> new LinkedHashMap<>());
            }
        }

        for (String aliasUpper : providerProps.keySet()) {
            String prefix = PROVIDER_ENV_PREFIX + aliasUpper + "_";
            Map<String, String> props = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    String propKey = entry.getKey().substring(prefix.length()).toLowerCase().replace('_', '-');
                    props.put(propKey, entry.getValue());
                }
            }
            String type = props.get("type");
            if (type == null)
                continue;
            props.remove("type");

            ProviderFactory factory = factories.get(type);
            if (factory == null) {
                log.warn("Unknown provider type '{}' for env alias '{}', skipping", type, aliasUpper);
                continue;
            }
            String alias = aliasUpper.toLowerCase().replace('_', '-');
            builder.provider(alias, factory.createConfig(alias, props));
        }

        // Discover service routing: AETHER_SERVICE_BLOB_STORE=prod-aws
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(SERVICE_ENV_PREFIX)) {
                String serviceKey = key.substring(SERVICE_ENV_PREFIX.length()).toLowerCase().replace('_', '-');
                if (ServiceTypes.REGISTRY.containsKey(serviceKey)) {
                    builder.routeByKey(serviceKey, entry.getValue());
                } else {
                    log.warn("Unknown service key '{}' from env var '{}', skipping", serviceKey, key);
                }
            }
        }

        return builder.build(factories);
    }

    private static String _interpolate(String raw, Map<String, String> env) {
        Matcher matcher = ENV_PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);
            String value = env.get(varName);
            if (value == null && defaultValue == null) {
                throw new InvalidConfigurationException("aether", "config.interpolate",
                        "Environment variable '" + varName + "' is not set and has no default value");
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : defaultValue));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static Map<String, ProviderFactory> _loadProviderFactories() {
        Map<String, ProviderFactory> factories = new LinkedHashMap<>();
        for (ProviderFactory factory : ServiceLoader.load(ProviderFactory.class,
                AetherConfigLoader.class.getClassLoader())) {
            String type = factory.providerType();
            if (factories.containsKey(type)) {
                log.warn("Duplicate ProviderFactory for type '{}': {} overrides {}", type, factory.getClass().getName(),
                        factories.get(type).getClass().getName());
            }
            factories.put(type, factory);
        }
        return factories;
    }

    private static AetherConfig _merge(AetherConfig base, AetherConfig override) {
        Map<String, ProviderConfig> merged = new LinkedHashMap<>(base.providers());
        merged.putAll(override.providers());
        Map<String, String> mergedRouting = new LinkedHashMap<>(base.serviceRouting());
        mergedRouting.putAll(override.serviceRouting());
        // Override factories win (both should be identical in practice)
        Map<String, ProviderFactory> mergedFactories = new LinkedHashMap<>(base.factories());
        mergedFactories.putAll(override.factories());
        return new AetherConfig(merged, mergedRouting, mergedFactories);
    }

    private static Map<String, String> _toStringMap(Map<String, Object> source, String... excludeKeys) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> excluded = Set.of(excludeKeys);
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!excluded.contains(entry.getKey()) && entry.getValue() != null) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
