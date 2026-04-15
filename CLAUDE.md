# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aether is a Java framework for building multi-cloud applications. It provides a unified API abstracting over AWS, GCP, and Azure, allowing cloud-agnostic code that deploys to any provider without changes. The project is in its foundation phase — build infrastructure and documentation exist, but core interfaces are not yet implemented.

## Build Commands

```bash
./gradlew build                    # Build all modules
./gradlew test                     # Run all tests
./gradlew :aether-core:test        # Run tests for a specific module
./gradlew clean build              # Clean and rebuild
```

Java 21 is required (enforced via Gradle toolchain in the convention plugin).

## Module Structure

```
aether-core   — Core abstractions and interfaces (no aether dependencies)
aether-aws    — AWS provider implementation (depends on aether-core)
aether-gcp    — GCP provider implementation (depends on aether-core)
aether-azure  — Azure provider implementation (depends on aether-core)
```

All modules apply the shared convention plugin `io.foundry.aether.java-conventions`.

## Build System Architecture

- **Kotlin DSL** for all Gradle build files
- **Convention plugin** at `buildSrc/src/main/kotlin/io/foundry/aether/AetherJavaConventionPlugin.kt` — applies `java-library`, configures Java 21 toolchain, sets up JUnit Platform, and adds common dependencies (SLF4J, Guava, Jackson, Commons Lang3, JUnit 5, Mockito, AssertJ)
- **Centralized versions** at `buildSrc/src/main/kotlin/io/foundry/aether/AetherVersions.kt` — all dependency versions in a single Kotlin object
- **BOM usage**: AWS and GCP modules use official BOMs for transitive dependency management; Azure does not
- Cloud SDKs are declared as `api` dependencies in provider modules to expose them to consumers

## Conventions

- **Naming**: Interfaces are descriptive nouns (`BlobStore`, `ComputeEngine`); implementations are provider-prefixed (`S3BlobStore`, `GcsBlobStore`); exceptions extend `CloudException`; config classes are `XxxConfig`
- **Package structure**: `io.foundry.aether.core`, `io.foundry.aether.config`, `io.foundry.aether.storage`, `io.foundry.aether.aws`, `io.foundry.aether.gcp`, `io.foundry.aether.azure`
- **Git workflow**: Conventional Commits (`type(scope): description`), branches named `feature/<desc>`, `fix/<desc>`, `docs/<desc>`, `chore/<desc>`
- **Testing**: JUnit 5 + Mockito + AssertJ; tests go in `src/test/java/` per module

## Key Documentation

- `docs/future/01-project-vision.md` — problem statement, unified API design, roadmap
- `docs/future/02-technical-architecture.md` — build rationale, dependency strategy, error handling, configuration design, decision log
- `docs/future/03-development-workflow.md` — prerequisites, module creation, coding standards, PR requirements
