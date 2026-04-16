/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryComputeEngineTest {

    private InMemoryComputeEngine engine;

    @BeforeEach
    void setUp() {
        engine = new InMemoryComputeEngine(new InMemoryCloudProvider());
    }

    @Test
    void createThenGet() {
        var config = new InstanceConfig("web-1", "t3.micro", "ami-123", "us-east-1", Map.of());
        var created = engine.createInstance(config);

        assertThat(created.state()).isEqualTo(InstanceState.RUNNING);
        assertThat(created.name()).isEqualTo("web-1");

        var fetched = engine.getInstance(created.instanceId());
        assertThat(fetched).isEqualTo(created);
    }

    @Test
    void terminateInstance() {
        var created = engine.createInstance(new InstanceConfig("x", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.terminateInstance(created.instanceId());

        assertThat(engine.getInstance(created.instanceId()).state()).isEqualTo(InstanceState.TERMINATED);
    }

    @Test
    void listInstances() {
        engine.createInstance(new InstanceConfig("a", "t3.micro", "ami-1", "us-east-1", Map.of()));
        engine.createInstance(new InstanceConfig("b", "t3.micro", "ami-1", "us-east-1", Map.of()));

        assertThat(engine.listInstances()).hasSize(2);
    }

    @Test
    void getMissingInstance_throws() {
        assertThatThrownBy(() -> engine.getInstance("nonexistent")).isInstanceOf(ResourceNotFoundException.class);
    }
}
