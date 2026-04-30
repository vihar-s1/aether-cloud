/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InsertInstanceRequest;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.gcp.GcpCloudProvider;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GcpComputeEngineTest {

    private static final String PROJECT = "test-project";
    private static final String ZONE = "us-central1-a";

    @Mock
    private GcpCloudProvider provider;
    @Mock
    private InstancesClient instancesClient;

    private GcpComputeEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(provider.name()).thenReturn("test-gcp");
        engine = new GcpComputeEngine(provider, instancesClient, PROJECT, ZONE);
    }

    @Test
    void getInstance_mapsFieldsCorrectly() {
        Instance instance = Instance.newBuilder().setId(42L).setName("web").setStatus("RUNNING")
                .setCreationTimestamp("2026-01-01T00:00:00+00:00").build();
        when(instancesClient.get(PROJECT, ZONE, "42")).thenReturn(instance);

        var info = engine.getInstance("42");

        assertThat(info.instanceId()).isEqualTo("42");
        assertThat(info.name()).isEqualTo("web");
        assertThat(info.state()).isEqualTo(InstanceState.RUNNING);
        assertThat(info.launchTimeMs()).isGreaterThan(0);
    }

    @Test
    void getInstance_notFound_throwsResourceNotFoundException() {
        ApiException notFound = _apiException(StatusCode.Code.NOT_FOUND);
        when(instancesClient.get(PROJECT, ZONE, "missing")).thenThrow(notFound);

        assertThatThrownBy(() -> engine.getInstance("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createInstance_callsInsertAndReturnsInfo() throws Exception {
        Instance created = Instance.newBuilder().setId(99L).setName("app").setStatus("RUNNING").build();
        OperationFuture future = mock(OperationFuture.class);
        when(future.get()).thenReturn(created);
        when(instancesClient.insertAsync(any(InsertInstanceRequest.class))).thenReturn(future);
        // createInstance calls getInstance(config.name()) after insert — stub by name
        when(instancesClient.get(PROJECT, ZONE, "app")).thenReturn(created);

        var info = engine.createInstance(
                new InstanceConfig("app", "n1-standard-1", "projects/test/global/images/debian", ZONE, Map.of()));

        assertThat(info.name()).isEqualTo("app");
        assertThat(info.instanceId()).isEqualTo("99");
        verify(instancesClient).insertAsync(any(InsertInstanceRequest.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void terminateInstance_callsDeleteAsync() throws Exception {
        OperationFuture future = mock(OperationFuture.class);
        when(future.get()).thenReturn(null);
        when(instancesClient.deleteAsync(eq(PROJECT), eq(ZONE), eq("42"))).thenReturn(future);

        engine.terminateInstance("42");

        verify(instancesClient).deleteAsync(PROJECT, ZONE, "42");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void terminateInstance_notFound_throwsResourceNotFoundException() throws Exception {
        ApiException notFound = _apiException(StatusCode.Code.NOT_FOUND);
        OperationFuture future = mock(OperationFuture.class);
        when(future.get()).thenThrow(new ExecutionException(notFound));
        when(instancesClient.deleteAsync(eq(PROJECT), eq(ZONE), eq("missing"))).thenReturn(future);

        assertThatThrownBy(() -> engine.terminateInstance("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mapInstanceState_coversAllKnownStatuses() {
        assertThat(_stateFor("RUNNING")).isEqualTo(InstanceState.RUNNING);
        assertThat(_stateFor("PROVISIONING")).isEqualTo(InstanceState.PENDING);
        assertThat(_stateFor("STAGING")).isEqualTo(InstanceState.PENDING);
        assertThat(_stateFor("STOPPING")).isEqualTo(InstanceState.STOPPING);
        assertThat(_stateFor("SUSPENDING")).isEqualTo(InstanceState.STOPPING);
        assertThat(_stateFor("STOPPED")).isEqualTo(InstanceState.STOPPED);
        assertThat(_stateFor("SUSPENDED")).isEqualTo(InstanceState.STOPPED);
        assertThat(_stateFor("TERMINATED")).isEqualTo(InstanceState.TERMINATED);
        assertThat(_stateFor("UNDEFINED_STATE")).isEqualTo(InstanceState.UNKNOWN);
    }

    @Test
    void getInstance_invalidTimestamp_launchTimeIsZero() {
        Instance instance = Instance.newBuilder().setId(1L).setName("x").setStatus("RUNNING")
                .setCreationTimestamp("not-a-date").build();
        when(instancesClient.get(PROJECT, ZONE, "1")).thenReturn(instance);

        var info = engine.getInstance("1");
        assertThat(info.launchTimeMs()).isEqualTo(0L);
    }

    @Test
    void getInstance_withLabels_tagsPreserved() {
        Instance instance = Instance.newBuilder().setId(7L).setName("tagged").setStatus("RUNNING")
                .putLabels("env", "prod").putLabels("team", "infra").build();
        when(instancesClient.get(PROJECT, ZONE, "7")).thenReturn(instance);

        var info = engine.getInstance("7");
        assertThat(info.tags()).containsEntry("env", "prod").containsEntry("team", "infra");
    }

    private InstanceState _stateFor(String gcpStatus) {
        Instance instance = Instance.newBuilder().setId(1L).setName("x").setStatus(gcpStatus).build();
        when(instancesClient.get(PROJECT, ZONE, "x")).thenReturn(instance);
        return engine.getInstance("x").state();
    }

    private ApiException _apiException(StatusCode.Code code) {
        // ApiException.getStatusCode() is final — can't be mocked. Use concrete subclass
        // with a mocked StatusCode interface (which is mockable).
        StatusCode statusCode = mock(StatusCode.class);
        when(statusCode.getCode()).thenReturn(code);
        return new NotFoundException(null, statusCode, false);
    }
}
