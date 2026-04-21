/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

import java.util.Optional;

/**
 * Represents a configured cloud provider instance. Owns the native SDK clients
 * for its provider.
 *
 * <p>
 * Lifecycle:
 * <ol>
 * <li>Construct — stores config, no connections yet.
 * <li>{@link #initialize()} — builds SDK clients, establishes connections. May
 * be retried if it fails (status becomes {@link ProviderStatus#FAILED}).
 * <li>Use — pass to service implementations which call the provider's client
 * getters.
 * <li>{@link #shutdown()} — closes all clients and releases resources. Terminal
 * — create a new instance to reuse.
 * </ol>
 */
public interface CloudProvider extends AutoCloseable {

    /**
     * Logical alias for this provider instance, e.g. {@code "prod-aws"},
     * {@code "local-nfs"}.
     */
    String name();

    /**
     * Initializes this provider by building SDK clients and establishing
     * connections.
     *
     * <p>
     * May be called again if the previous attempt resulted in
     * {@link ProviderStatus#FAILED}.
     *
     * @throws IllegalStateException
     *             if status is {@link ProviderStatus#RUNNING} or
     *             {@link ProviderStatus#SHUTDOWN}
     */
    void initialize();

    /**
     * Closes all SDK clients and releases resources held by this provider. Terminal
     * — create a new instance to reuse.
     *
     * @throws IllegalStateException
     *             if status is not {@link ProviderStatus#RUNNING}
     */
    void shutdown();

    /** Returns the current lifecycle status of this provider. */
    ProviderStatus status();

    /**
     * Returns the exception thrown by the most recent failed {@link #initialize()}
     * call, or empty if the provider has never failed.
     */
    Optional<Throwable> failureCause();

    default void close() {
        if (status() == ProviderStatus.RUNNING) {
            shutdown();
        }
    }
}
