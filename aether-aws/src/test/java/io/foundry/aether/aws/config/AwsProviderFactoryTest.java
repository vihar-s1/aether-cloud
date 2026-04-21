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
        assertThat(config.accessKey()).isEqualTo("AKIA123");
        assertThat(config.secretKey()).isEqualTo("secret");
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
    void createConfig_missingAccessKey_throws() {
        var props = Map.of("secret-key", "secret", "region", "us-east-1");

        assertThatThrownBy(() -> factory.createConfig("prod-aws", props))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("access-key");
    }

    @Test
    void createConfig_missingSecretKey_throws() {
        var props = Map.of("access-key", "AKIA123", "region", "us-east-1");

        assertThatThrownBy(() -> factory.createConfig("prod-aws", props))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("secret-key");
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
