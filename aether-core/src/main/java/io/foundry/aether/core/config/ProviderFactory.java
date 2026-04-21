/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import io.foundry.aether.core.CloudService;
import java.util.Map;
import java.util.Optional;

/**
 * SPI contract for provider modules.
 *
 * <p>
 * Each provider module ships exactly one implementation, registered in
 * {@code META-INF/services/io.foundry.aether.core.config.ProviderFactory}. A
 * single implementation handles both config construction and service
 * instantiation for its provider type.
 */
public interface ProviderFactory {

    /**
     * Provider type identifier, e.g. {@code "aws"}, {@code "nfs"},
     * {@code "inmemory"}.
     */
    String providerType();

    /**
     * Builds a {@link ProviderConfig} from a logical alias and normalized property
     * keys (lowercase-hyphen).
     *
     * @param alias
     *            the logical name assigned to this provider instance, e.g.
     *            {@code "prod-aws"}
     * @param props
     *            normalized properties (keys lowercase-hyphen, e.g.
     *            {@code "access-key"})
     */
    ProviderConfig createConfig(String alias, Map<String, String> props);

    /**
     * Creates a service of the requested type, or returns empty if this provider
     * does not support it.
     */
    <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType);
}
