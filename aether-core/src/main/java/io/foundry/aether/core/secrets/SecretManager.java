/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.CloudException;
import java.util.List;

public interface SecretManager extends CloudService {

    SecretValue getSecret(String secretId) throws CloudException;

    SecretMetadata putSecret(String secretId, String value) throws CloudException;

    SecretValue rotate(String secretId) throws CloudException;

    void deleteSecret(String secretId) throws CloudException;

    List<SecretMetadata> listSecrets() throws CloudException;
}
