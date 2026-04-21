/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws;

import static org.assertj.core.api.Assertions.*;

import io.foundry.aether.aws.config.AwsProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AwsCloudProvider")
class AwsCloudProviderTest {

    @Test
    @DisplayName("name() returns alias from config")
    void name_returnsAlias() {
        AwsProviderConfig config = AwsProviderConfig.builder().name("prod-aws").accessKey("AKIA123").secretKey("secret")
                .region("us-east-1").build();

        var provider = new AwsCloudProvider(config);
        provider.initialize();
        assertThat(provider.name()).isEqualTo("prod-aws");
        provider.shutdown();
    }

    @Test
    @DisplayName("shutdown() closes clients without throwing")
    void shutdown_closesClients() {
        AwsProviderConfig config = AwsProviderConfig.builder().name("test-aws").accessKey("AKIA123").secretKey("secret")
                .region("us-east-1").build();

        var provider = new AwsCloudProvider(config);
        provider.initialize();
        assertThatNoException().isThrownBy(provider::shutdown);
    }

    @Test
    @DisplayName("clients are non-null after initialize()")
    void clients_nonNull() {
        AwsProviderConfig config = AwsProviderConfig.builder().name("test-aws").accessKey("AKIA123").secretKey("secret")
                .region("us-east-1").build();

        var provider = new AwsCloudProvider(config);
        provider.initialize();
        assertThat(provider.s3Client()).isNotNull();
        assertThat(provider.secretsManagerClient()).isNotNull();
        assertThat(provider.ec2Client()).isNotNull();
        provider.shutdown();
    }

    @Test
    @DisplayName("throws when only accessKey is provided without secretKey")
    void partialCredentials_accessKeyOnly_throws() {
        assertThatThrownBy(
                () -> AwsProviderConfig.builder().name("test-aws").accessKey("AKIA123").region("us-east-1").build())
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("must both be provided or both be absent");
    }

    @Test
    @DisplayName("throws when only secretKey is provided without accessKey")
    void partialCredentials_secretKeyOnly_throws() {
        assertThatThrownBy(
                () -> AwsProviderConfig.builder().name("test-aws").secretKey("secret").region("us-east-1").build())
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("must both be provided or both be absent");
    }

    @Test
    @DisplayName("no credentials builds successfully (IAM / default credential chain)")
    void noCredentials_iamMode_succeeds() {
        AwsProviderConfig config = AwsProviderConfig.builder().name("test-aws").region("us-east-1").build();

        var provider = new AwsCloudProvider(config);
        provider.initialize();
        assertThat(provider.s3Client()).isNotNull();
        provider.shutdown();
    }
}
