/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CloudExceptionTest {

    @Test
    void authenticationException_isNeverRetryable() {
        var ex = new AuthenticationException("aws", "upload", "Bad credentials");
        assertThat(ex.retryable()).isFalse();
        assertThat(ex.providerName()).isEqualTo("aws");
        assertThat(ex.operation()).isEqualTo("upload");
        assertThat(ex.getMessage()).contains("[aws/upload]").contains("Bad credentials");
    }

    @Test
    void resourceNotFoundException_carriesResourceId() {
        var ex = new ResourceNotFoundException("gcp", "download", "my-bucket", "Bucket not found");
        assertThat(ex.retryable()).isFalse();
        assertThat(ex.resourceId()).isEqualTo("my-bucket");
    }

    @Test
    void quotaExceededException_isAlwaysRetryable() {
        var ex = new QuotaExceededException("azure", "createInstance", "Rate limit hit");
        assertThat(ex.retryable()).isTrue();
    }

    @Test
    void providerUnavailableException_isAlwaysRetryable() {
        var cause = new RuntimeException("connection refused");
        var ex = new ProviderUnavailableException("aws", "listInstances", "Provider down", cause);
        assertThat(ex.retryable()).isTrue();
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void invalidConfigurationException_isNeverRetryable() {
        var ex = new InvalidConfigurationException("gcp", "initialize", "Missing project ID");
        assertThat(ex.retryable()).isFalse();
    }

    @Test
    void sealedHierarchy_exhaustiveSwitch() {
        CloudException ex = new AuthenticationException("aws", "op", "msg");
        String result =
                switch (ex) {
                    case AuthenticationException e -> "auth";
                    case ResourceNotFoundException e -> "notfound";
                    case QuotaExceededException e -> "quota";
                    case ProviderUnavailableException e -> "unavailable";
                    case InvalidConfigurationException e -> "config";
                };
        assertThat(result).isEqualTo("auth");
    }
}
