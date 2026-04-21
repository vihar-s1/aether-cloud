/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.secrets;

import io.foundry.aether.core.contract.SecretManagerContractTest;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

public class NFSSecretManagerContractTest extends SecretManagerContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected SecretManager createSecretManager() {
        NFSCloudProvider provider = new NFSCloudProvider("test-nfs", tempDir.toString());
        provider.initialize();
        return new NFSSecretManager(provider);
    }
}
