/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import java.util.Map;

/**
 * SPI for converting a raw property map into a typed {@link ProviderConfig}.
 *
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} and
 * registered in
 * {@code META-INF/services/io.foundry.aether.core.config.ProviderConfigFactory}.
 *
 * <p>
 * Property map keys use lowercase-hyphen format matching YAML field names (e.g.
 * {@code "access-key"}, {@code "root-path"}).
 */
public interface ProviderConfigFactory {

    /** The provider type string this factory handles, e.g. {@code "aws"}. */
    String providerType();

    /**
     * Convert raw key/value properties into a typed {@link ProviderConfig}. Throws
     * {@link io.foundry.aether.core.exception.InvalidConfigurationException} for
     * missing required fields.
     */
    ProviderConfig create(Map<String, String> rawProperties);
}
