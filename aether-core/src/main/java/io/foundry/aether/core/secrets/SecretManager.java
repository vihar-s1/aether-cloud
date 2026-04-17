/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import io.foundry.aether.core.CloudService;
import java.util.List;

public interface SecretManager extends CloudService {

    String SECRET = "secret";

    @Override
    default String serviceName() {
        return "secret-manager";
    }

    SecretValue getSecret(String secretId);

    SecretMetadata createSecret(String secretId, String value);

    SecretMetadata updateSecret(String secretId, String value);

    SecretValue rotate(String secretId);

    void deleteSecret(String secretId);

    List<SecretMetadata> listSecrets();

    /**
     * Put a secret (create if doesn't exist, update if it does).
     */
    default SecretMetadata putSecret(String secretId, String value) {
        try {
            return updateSecret(secretId, value);
        } catch (Exception e) {
            return createSecret(secretId, value);
        }
    }
}
