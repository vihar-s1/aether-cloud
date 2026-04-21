/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.secrets;

import io.foundry.aether.core.contract.SecretManagerContractTest;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.inmemory.InMemoryCloudProvider;

class InMemorySecretManagerContractTest extends SecretManagerContractTest {

    @Override
    protected SecretManager createSecretManager() {
        InMemoryCloudProvider provider = new InMemoryCloudProvider("test-inmemory");
        provider.initialize();
        return new InMemorySecretManager(provider);
    }
}
