/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.List;

public interface SecretManager extends CloudService {

    String SECRET = "secret";

    @Override
    default String serviceName() {
        return "secret-manager";
    }

    /**
     * Returns the current value of a secret.
     *
     * @throws ResourceNotFoundException
     *             if the secret does not exist
     */
    SecretValue getSecret(String secretId);

    /**
     * Creates a new secret with the given value.
     *
     * @throws io.foundry.aether.core.exception.InvalidConfigurationException
     *             if a secret with this ID already exists
     */
    SecretMetadata createSecret(String secretId, String value);

    /**
     * Replaces the value of an existing secret with a new version.
     *
     * @throws ResourceNotFoundException
     *             if the secret does not exist
     */
    SecretMetadata updateSecret(String secretId, String value);

    /**
     * Creates a new version of a secret carrying the same value, so the previous
     * version can be expired or revoked without changing the active value.
     *
     * @throws ResourceNotFoundException
     *             if the secret does not exist
     */
    SecretValue rotate(String secretId);

    /**
     * Permanently deletes a secret and all its versions.
     *
     * @throws ResourceNotFoundException
     *             if the secret does not exist
     */
    void deleteSecret(String secretId);

    /**
     * Returns all secrets. Note: the {@code versionId} field of each returned
     * {@link SecretMetadata} is {@code null} for cloud providers (AWS, GCP) because
     * list APIs do not return version information. Use {@link #getSecret(String)}
     * to obtain the current version.
     */
    List<SecretMetadata> listSecrets();

    /**
     * Creates the secret if it does not exist, or updates it if it does.
     */
    default SecretMetadata putSecret(String secretId, String value) {
        try {
            return updateSecret(secretId, value);
        } catch (ResourceNotFoundException e) {
            return createSecret(secretId, value);
        }
    }
}
