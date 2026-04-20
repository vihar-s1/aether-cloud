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

class AwsProviderConfigFactoryTest {

    private final AwsProviderConfigFactory factory = new AwsProviderConfigFactory();

    @Test
    void providerType_isAws() {
        assertThat(factory.providerType()).isEqualTo("aws");
    }

    @Test
    void create_allFieldsPresent_returnsConfig() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret", "region", "us-east-1", "endpoint",
                "http://localhost:4566");

        AwsProviderConfig config = (AwsProviderConfig) factory.create(props);

        assertThat(config.accessKey()).isEqualTo("AKIA123");
        assertThat(config.secretKey()).isEqualTo("secret");
        assertThat(config.region()).isEqualTo("us-east-1");
        assertThat(config.endpoint()).hasValue("http://localhost:4566");
        assertThat(config.providerType()).isEqualTo("aws");
    }

    @Test
    void create_endpointAbsent_returnsEmptyOptional() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret", "region", "us-east-1");

        AwsProviderConfig config = (AwsProviderConfig) factory.create(props);

        assertThat(config.endpoint()).isEmpty();
    }

    @Test
    void create_missingAccessKey_throws() {
        var props = Map.of("secret-key", "secret", "region", "us-east-1");

        assertThatThrownBy(() -> factory.create(props)).isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("access-key");
    }

    @Test
    void create_missingSecretKey_throws() {
        var props = Map.of("access-key", "AKIA123", "region", "us-east-1");

        assertThatThrownBy(() -> factory.create(props)).isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("secret-key");
    }

    @Test
    void create_missingRegion_throws() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret");

        assertThatThrownBy(() -> factory.create(props)).isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("region");
    }

    @Test
    void create_blankRegion_throws() {
        var props = Map.of("access-key", "AKIA123", "secret-key", "secret", "region", "  ");

        assertThatThrownBy(() -> factory.create(props)).isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("region");
    }
}
