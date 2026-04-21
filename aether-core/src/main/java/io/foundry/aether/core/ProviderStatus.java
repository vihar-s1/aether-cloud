/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

/**
 * Lifecycle states for a {@link CloudProvider} instance.
 *
 * <p>
 * Valid transitions:
 *
 * <pre>
 *   UNINITIALIZED → RUNNING    (initialize() succeeded)
 *   UNINITIALIZED → FAILED     (initialize() threw)
 *   FAILED        → RUNNING    (initialize() retried and succeeded)
 *   FAILED        → FAILED     (initialize() retried and failed again)
 *   RUNNING       → SHUTDOWN   (shutdown() called)
 * </pre>
 *
 * <p>
 * SHUTDOWN is terminal — construct a new provider to start again.
 */
public enum ProviderStatus {
    /** Freshly constructed, {@code initialize()} has not been called yet. */
    UNINITIALIZED,
    /**
     * {@code initialize()} completed successfully; the provider is ready to use.
     */
    RUNNING,
    /** {@code initialize()} threw; {@code initialize()} may be retried. */
    FAILED,
    /**
     * {@code shutdown()} was called; terminal state — create a new instance to
     * reuse.
     */
    SHUTDOWN
}
