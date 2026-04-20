/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import io.foundry.aether.core.CloudService;

/**
 * SPI for creating a {@link CloudService} from a {@link ProviderConfig}.
 *
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} and
 * registered in
 * {@code META-INF/services/io.foundry.aether.core.config.CloudServiceFactory}.
 *
 * <p>
 * Each factory covers one (providerType, serviceType) pair, e.g. AWS +
 * BlobStore.
 */
public interface CloudServiceFactory {

    /** Provider type this factory handles, e.g. {@code "aws"}. */
    String providerType();

    /** Service interface this factory produces, e.g. {@code BlobStore.class}. */
    Class<? extends CloudService> serviceType();

    /**
     * Create a service instance from the given provider config. The config is
     * guaranteed to be of the type corresponding to {@link #providerType()}.
     */
    CloudService create(ProviderConfig config);
}
