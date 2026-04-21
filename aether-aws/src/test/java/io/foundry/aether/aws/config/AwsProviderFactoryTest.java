/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.exception.InvalidConfigurationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwsProviderFactoryTest {

    private final AwsProviderFactory factory = new AwsProviderFactory();

    @Test
    void providerType_isAws() {
        assertThat(factory.providerType()).isEqualTo("aws");
    }

    @Test
    void createConfig_allFieldsPresent_returnsConfig() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret", "region", "us-east-1", "endpoint",
                "http://localhost:4566");

        AwsProviderConfig config = (AwsProviderConfig) factory.createConfig("prod-aws", props);

        assertThat(config.name()).isEqualTo("prod-aws");
        assertThat(config.accessKey()).hasValue("AKIA123");
        assertThat(config.secretKey()).hasValue("secret");
        assertThat(config.region()).isEqualTo("us-east-1");
        assertThat(config.endpoint()).hasValue("http://localhost:4566");
        assertThat(config.providerType()).isEqualTo("aws");
    }

    @Test
    void createConfig_endpointAbsent_returnsEmptyOptional() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret", "region", "us-east-1");

        AwsProviderConfig config = (AwsProviderConfig) factory.createConfig("prod-aws", props);

        assertThat(config.endpoint()).isEmpty();
    }

    @Test
    void createConfig_noCredentials_succeeds() {
        var props = Map.of("region", "us-east-1");

        AwsProviderConfig config = (AwsProviderConfig) factory.createConfig("prod-aws", props);

        assertThat(config.accessKey()).isEmpty();
        assertThat(config.secretKey()).isEmpty();
    }

    @Test
    void createConfig_accessKeyWithoutSecretKey_throws() {
        var props = Map.of("access-key", "AKIA123", "region", "us-east-1");

        assertThatThrownBy(() -> factory.createConfig("prod-aws", props))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("must both be provided or both be absent");
    }

    @Test
    void createConfig_secretKeyWithoutAccessKey_throws() {
        var props = Map.of("secret-key", "secret", "region", "us-east-1");

        assertThatThrownBy(() -> factory.createConfig("prod-aws", props))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("must both be provided or both be absent");
    }

    @Test
    void createConfig_missingRegion_throws() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret");

        assertThatThrownBy(() -> factory.createConfig("prod-aws", props))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("region");
    }

    @Test
    void createConfig_blankRegion_throws() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret", "region", "  ");

        assertThatThrownBy(() -> factory.createConfig("prod-aws", props))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("region");
    }
}
