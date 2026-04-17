/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws;

import static org.assertj.core.api.Assertions.*;

import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AwsCloudProvider")
class AwsCloudProviderTest {

    @Test
    @DisplayName("initializes with valid credentials and region")
    void testInitialization() {
        var provider =
                new AwsCloudProvider("test-key", "test-secret", "https://s3.amazonaws.com", "us-east-1");

        assertThat(provider.name()).isEqualTo("aws");
        assertThat(provider.accessKey()).isEqualTo("test-key");
        assertThat(provider.secretKey()).isEqualTo("test-secret");
        assertThat(provider.endpoint()).isEqualTo("https://s3.amazonaws.com");
        assertThat(provider.region()).isEqualTo("us-east-1");
        assertThat(provider.status()).isEqualTo(ProviderStatus.INITIALIZED);
    }

    @Test
    @DisplayName("transitions status from INITIALIZED to RUNNING")
    void testInitializeTransition() {
        var provider = new AwsCloudProvider("key", "secret", null, "us-west-2");

        assertThat(provider.status()).isEqualTo(ProviderStatus.INITIALIZED);
        provider.initialize();
        assertThat(provider.status()).isEqualTo(ProviderStatus.RUNNING);
    }

    @Test
    @DisplayName("transitions status from RUNNING to SHUTDOWN")
    void testShutdownTransition() {
        var provider = new AwsCloudProvider("key", "secret", null, "eu-west-1");
        provider.initialize();

        provider.shutdown();
        assertThat(provider.status()).isEqualTo(ProviderStatus.SHUTDOWN);
    }

    @Test
    @DisplayName("throws when initializing already-initialized provider")
    void testDoubleInitializeThrows() {
        var provider = new AwsCloudProvider("key", "secret", null, "us-east-1");
        provider.initialize();

        assertThatThrownBy(provider::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provider already initialized");
    }

    @Test
    @DisplayName("throws when shutting down already-shutdown provider")
    void testDoubleShutdownThrows() {
        var provider = new AwsCloudProvider("key", "secret", null, "us-east-1");
        provider.initialize();
        provider.shutdown();

        assertThatThrownBy(provider::shutdown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provider already shutdown");
    }

    @Test
    @DisplayName("throws InvalidConfigurationException when accessKey is null")
    void testNullAccessKeyThrows() {
        assertThatThrownBy(() -> new AwsCloudProvider(null, "secret", null, "us-east-1"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("accessKey must not be null or empty");
    }

    @Test
    @DisplayName("throws InvalidConfigurationException when secretKey is null")
    void testNullSecretKeyThrows() {
        assertThatThrownBy(() -> new AwsCloudProvider("key", null, null, "us-east-1"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("secretKey must not be null or empty");
    }

    @Test
    @DisplayName("throws InvalidConfigurationException when region is null")
    void testNullRegionThrows() {
        assertThatThrownBy(() -> new AwsCloudProvider("key", "secret", null, null))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("region must not be null or empty");
    }

    @Test
    @DisplayName("allows null endpoint (uses default AWS endpoint)")
    void testNullEndpointAllowed() {
        var provider = new AwsCloudProvider("key", "secret", null, "us-east-1");
        assertThat(provider.endpoint()).isNull();
    }

    @Test
    @DisplayName("throws InvalidConfigurationException when accessKey is empty")
    void testEmptyAccessKeyThrows() {
        assertThatThrownBy(() -> new AwsCloudProvider("", "secret", null, "us-east-1"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("accessKey must not be null or empty");
    }

    @Test
    @DisplayName("throws InvalidConfigurationException when secretKey is empty")
    void testEmptySecretKeyThrows() {
        assertThatThrownBy(() -> new AwsCloudProvider("key", "", null, "us-east-1"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("secretKey must not be null or empty");
    }

    @Test
    @DisplayName("throws InvalidConfigurationException when region is empty")
    void testEmptyRegionThrows() {
        assertThatThrownBy(() -> new AwsCloudProvider("key", "secret", null, ""))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("region must not be null or empty");
    }
}
