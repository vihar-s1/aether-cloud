/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import io.foundry.aether.core.CloudService;
import java.util.List;

public interface SecretManager extends CloudService {

    @Override
    default String serviceName() {
        return "secret-manager";
    }

    SecretValue getSecret(String secretId);

    SecretMetadata putSecret(String secretId, String value);

    SecretValue rotate(String secretId);

    void deleteSecret(String secretId);

    List<SecretMetadata> listSecrets();
}
