/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.rest.PagedIterable;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.PowerState;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.config.AzureProviderConfig;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AzureComputeEngineTest {

    @Mock
    private ComputeManager computeManager;
    @Mock
    private VirtualMachines virtualMachines;

    private AzureComputeEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        AzureProviderConfig config = AzureProviderConfig.builder().name("test-azure").storageAccount("devstoreaccount1")
                .subscriptionId("sub-123").resourceGroup("rg-test")
                .enable(io.foundry.aether.core.compute.ComputeEngine.class).build();
        AzureCloudProvider provider = new AzureCloudProvider(config);
        when(computeManager.virtualMachines()).thenReturn(virtualMachines);
        engine = new AzureComputeEngine(provider, computeManager);
    }

    @Test
    void getInstance_found_returnsInfo() {
        VirtualMachine vm = _mockVm("/subscriptions/sub-123/resourceGroups/rg-test/virtualMachines/web", "web",
                PowerState.RUNNING);
        when(virtualMachines.getByResourceGroup("rg-test", "web")).thenReturn(vm);

        var info = engine.getInstance("web");

        assertThat(info.name()).isEqualTo("web");
        assertThat(info.state()).isEqualTo(InstanceState.RUNNING);
        assertThat(info.instanceId()).isEqualTo("/subscriptions/sub-123/resourceGroups/rg-test/virtualMachines/web");
    }

    @Test
    void getInstance_notFound_throwsResourceNotFoundException() {
        when(virtualMachines.getByResourceGroup("rg-test", "missing")).thenReturn(null);

        assertThatThrownBy(() -> engine.getInstance("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void terminateInstance_callsDeleteById() {
        VirtualMachine vm = _mockVm("/subscriptions/sub-123/resourceGroups/rg-test/virtualMachines/web", "web",
                PowerState.RUNNING);
        when(virtualMachines.getByResourceGroup("rg-test", "web")).thenReturn(vm);

        engine.terminateInstance("web");

        verify(virtualMachines).deleteById("/subscriptions/sub-123/resourceGroups/rg-test/virtualMachines/web");
    }

    @Test
    void terminateInstance_notFound_throwsResourceNotFoundException() {
        when(virtualMachines.getByResourceGroup("rg-test", "missing")).thenReturn(null);

        assertThatThrownBy(() -> engine.terminateInstance("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listInstances_returnsAll() {
        VirtualMachine vm1 = _mockVm("/id/vm1", "vm1", PowerState.RUNNING);
        VirtualMachine vm2 = _mockVm("/id/vm2", "vm2", PowerState.DEALLOCATED);
        // Build paged mock before passing to when()
        PagedIterable<VirtualMachine> paged = _pagedList(List.of(vm1, vm2));
        when(virtualMachines.listByResourceGroup("rg-test")).thenReturn(paged);

        var result = engine.listInstances(ListRequest.first());

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).name()).isEqualTo("vm1");
        assertThat(result.items().get(0).state()).isEqualTo(InstanceState.RUNNING);
        assertThat(result.items().get(1).state()).isEqualTo(InstanceState.DEALLOCATED);
    }

    @Test
    void listInstances_withLimit_paginates() {
        VirtualMachine vm1 = _mockVm("/id/vm1", "vm1", PowerState.RUNNING);
        VirtualMachine vm2 = _mockVm("/id/vm2", "vm2", PowerState.RUNNING);
        VirtualMachine vm3 = _mockVm("/id/vm3", "vm3", PowerState.RUNNING);
        PagedIterable<VirtualMachine> paged = _pagedList(List.of(vm1, vm2, vm3));
        when(virtualMachines.listByResourceGroup("rg-test")).thenReturn(paged);

        var page = engine.listInstances(ListRequest.withOffset(0, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void powerState_deallocated_mapsToDeallocated() {
        VirtualMachine vm = _mockVm("/id/vm", "vm", PowerState.DEALLOCATED);
        when(virtualMachines.getByResourceGroup("rg-test", "vm")).thenReturn(vm);

        var info = engine.getInstance("vm");
        assertThat(info.state()).isEqualTo(InstanceState.DEALLOCATED);
    }

    @Test
    void powerState_deallocating_mapsToDeallocating() {
        VirtualMachine vm = _mockVm("/id/vm", "vm", PowerState.DEALLOCATING);
        when(virtualMachines.getByResourceGroup("rg-test", "vm")).thenReturn(vm);

        var info = engine.getInstance("vm");
        assertThat(info.state()).isEqualTo(InstanceState.DEALLOCATING);
    }

    @Test
    void powerState_starting_mapsToPending() {
        VirtualMachine vm = _mockVm("/id/vm", "vm", PowerState.STARTING);
        when(virtualMachines.getByResourceGroup("rg-test", "vm")).thenReturn(vm);

        var info = engine.getInstance("vm");
        assertThat(info.state()).isEqualTo(InstanceState.PENDING);
    }

    @Test
    void tags_arePreserved() {
        VirtualMachine vm = _mockVm("/id/vm", "vm", PowerState.RUNNING);
        when(vm.tags()).thenReturn(Map.of("env", "prod", "team", "infra"));
        when(virtualMachines.getByResourceGroup("rg-test", "vm")).thenReturn(vm);

        var info = engine.getInstance("vm");
        assertThat(info.tags()).containsEntry("env", "prod").containsEntry("team", "infra");
    }

    private VirtualMachine _mockVm(String id, String name, PowerState powerState) {
        VirtualMachine vm = mock(VirtualMachine.class);
        when(vm.id()).thenReturn(id);
        when(vm.name()).thenReturn(name);
        when(vm.powerState()).thenReturn(powerState);
        when(vm.tags()).thenReturn(Map.of());
        when(vm.getPrimaryNetworkInterface()).thenReturn(null);
        return vm;
    }

    @SuppressWarnings("unchecked")
    private PagedIterable<VirtualMachine> _pagedList(List<VirtualMachine> vms) {
        PagedIterable<VirtualMachine> paged = mock(PagedIterable.class);
        // Set up forEach before this mock is used in a when() call
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<VirtualMachine> consumer = inv.getArgument(0);
            vms.forEach(consumer);
            return null;
        }).when(paged).forEach(org.mockito.ArgumentMatchers.any());
        return paged;
    }
}
