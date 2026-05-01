/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class ComputeEngineContractTest {

    protected abstract ComputeEngine createComputeEngine();

    private ComputeEngine engine;

    @BeforeEach
    protected void setUp() {
        engine = createComputeEngine();
    }

    @Test
    void createInstance_returnsInstanceWithId() {
        var info = engine.createInstance(InstanceConfig.of("web", "t3.micro", "ami-1", "us-east-1", Map.of()));
        assertThat(info.state()).isNotNull();
        assertThat(info.name()).isEqualTo("web");
        assertThat(info.instanceId()).isNotBlank();
    }

    @Test
    void getInstance_returnsSameInstanceId() {
        var created = engine.createInstance(InstanceConfig.of("web", "t3.micro", "ami-1", "us-east-1", Map.of()));
        var fetched = engine.getInstance(created.instanceId());
        assertThat(fetched.instanceId()).isEqualTo(created.instanceId());
        assertThat(fetched.name()).isEqualTo(created.name());
    }

    @Test
    void terminateThenGet_stateIsTerminated() {
        var created = engine.createInstance(InstanceConfig.of("web", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.terminateInstance(created.instanceId());
        assertThat(engine.getInstance(created.instanceId()).state()).isEqualTo(InstanceState.TERMINATED);
    }

    @Test
    void listAfterCreatingMultiple_containsBothInstances() {
        var a = engine.createInstance(InstanceConfig.of("a", "t3.micro", "ami-1", "us-east-1", Map.of()));
        var b = engine.createInstance(InstanceConfig.of("b", "t3.micro", "ami-1", "us-east-1", Map.of()));
        var ids = engine.listInstances(ListRequest.first()).items().stream().map(i -> i.instanceId()).toList();
        assertThat(ids).contains(a.instanceId(), b.instanceId());
    }

    @Test
    void getNonexistent_throws() {
        assertThatThrownBy(() -> engine.getInstance("no-such-id")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    protected void terminateNonexistent_throws() {
        assertThatThrownBy(() -> engine.terminateInstance("no-such-id")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createInstance_initialStateIsNotTerminated() {
        var info = engine.createInstance(InstanceConfig.of("web", "t3.micro", "ami-1", "us-east-1", Map.of()));
        assertThat(info.state()).isNotEqualTo(InstanceState.TERMINATED);
    }

    @Test
    void listInstances_afterTerminate_stillContainsInstance() {
        var created = engine.createInstance(InstanceConfig.of("web", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.terminateInstance(created.instanceId());
        var ids = engine.listInstances(ListRequest.first()).items().stream().map(i -> i.instanceId()).toList();
        assertThat(ids).contains(created.instanceId());
    }

    @Test
    void listInstances_withLimit_paginates() {
        engine.createInstance(InstanceConfig.of("a", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.createInstance(InstanceConfig.of("b", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.createInstance(InstanceConfig.of("c", "t3.micro", "ami-1", "us-east-1", Map.of()));

        var page = engine.listInstances(ListRequest.withOffset(0, 2));
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void createInstance_withTags_tagsPreserved() {
        var tags = Map.of("env", "prod", "team", "infra");
        var created = engine.createInstance(InstanceConfig.of("web", "t3.micro", "ami-1", "us-east-1", tags));
        var fetched = engine.getInstance(created.instanceId());
        assertThat(fetched.tags()).containsAllEntriesOf(tags);
    }

    @Test
    void createMultiple_allHaveUniqueIds() {
        var ids = new HashSet<String>();
        for (int i = 0; i < 5; i++) {
            ids.add(engine.createInstance(InstanceConfig.of("inst-" + i, "t3.micro", "ami-1", "us-east-1", Map.of()))
                    .instanceId());
        }
        assertThat(ids).hasSize(5);
    }

    @Test
    void listInstances_deterministicOrder() {
        engine.createInstance(InstanceConfig.of("a", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.createInstance(InstanceConfig.of("b", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.createInstance(InstanceConfig.of("c", "t3.micro", "ami-1", "us-east-1", Map.of()));

        var ids1 = engine.listInstances(ListRequest.first()).items().stream().map(i -> i.instanceId()).toList();
        var ids2 = engine.listInstances(ListRequest.first()).items().stream().map(i -> i.instanceId()).toList();
        assertThat(ids1).isEqualTo(ids2);
    }
}
