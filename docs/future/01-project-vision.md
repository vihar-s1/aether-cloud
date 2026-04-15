# Aether — Multi-Cloud Platform Framework

## Vision

Aether is a **Java framework for building multi-cloud applications**. It provides a unified API that abstracts over cloud providers (AWS, GCP, Azure), allowing developers to write cloud-agnostic code and deploy to any provider — or multiple providers simultaneously — without changing their application logic.

Think of it like **Spring Boot for cloud services**: instead of learning three different SDKs and wiring them together, you use Aether's consistent interfaces and swap providers via configuration.

---

## Problem Statement

Building applications that run on multiple cloud providers is hard because:

1. **Each cloud has its own SDK** — AWS SDK, Google Cloud Client Library, Azure SDK. They have different APIs, naming conventions, error handling, and authentication models.

2. **Vendor lock-in** — once you write code against AWS S3, migrating to GCP Cloud Storage means rewriting every storage call.

3. **Multi-cloud deployments** — some organizations need to run workloads across clouds (compliance, cost optimization, redundancy). Managing this manually is error-prone.

4. **Testing complexity** — testing against a real cloud provider requires credentials, network access, and costs money. A local/mock testing story is often missing.

---

## What Aether Does

### Unified API

Aether defines **provider-agnostic interfaces** for common cloud services:

| Service | Interface | Example Operations |
|---------|-----------|-------------------|
| Storage | `BlobStore` | `upload()`, `download()`, `list()`, `delete()` |
| Compute | `ComputeEngine` | `createInstance()`, `terminate()`, `list()` |
| Database | `DatabaseManager` | `create()`, `connect()`, `backup()` |
| Messaging | `MessageQueue` | `send()`, `receive()`, `createQueue()` |
| Secrets | `SecretManager` | `getSecret()`, `rotate()`, `putSecret()` |
| Networking | `NetworkManager` | `createVPC()`, `addSubnet()`, `configureFirewall()` |

### Provider Implementations

Each cloud provider implements these interfaces:

```
BlobStore
├── S3BlobStore          (AWS)
├── GcsBlobStore         (GCP)
└── AzureBlobStore       (Azure)
```

### Configuration-Driven Provider Selection

Switch providers without code changes:

```yaml
aether:
  provider: aws
  region: us-east-1
  storage:
    bucket: my-app-data
```

Change to GCP:

```yaml
aether:
  provider: gcp
  project: my-project
  storage:
    bucket: my-app-data
```

### Multi-Cloud Support

Run different services on different providers:

```yaml
aether:
  providers:
    storage: aws
    messaging: gcp
    secrets: azure
```

---

## What Aether Is NOT

- **Not an IaC tool** — Aether is not Terraform, Pulumi, or CloudFormation. It doesn't define infrastructure as code. It's a **runtime library** your application uses to interact with cloud services.

- **Not a deployment orchestrator** — Aether doesn't deploy your app to Kubernetes or manage containers. It's the library your app uses to talk to cloud APIs.

- **Not a cloud management platform** — Aether is not a dashboard or control plane. It's a **code-first framework**.

---

## Target Users

1. **Enterprise teams** building applications that must run on multiple clouds for compliance or business reasons.

2. **SaaS companies** that want to offer their customers a choice of cloud provider.

3. **Consultancies** that build solutions for clients across different cloud ecosystems.

4. **Developers** who want to avoid vendor lock-in from day one.

---

## Design Principles

### 1. Provider-Agnostic by Default
The primary API should not expose provider-specific concepts. If you need provider-specific features, you can drop down to the native SDK.

### 2. Zero Configuration Sensible Defaults
Aether should work with minimal config. Defaults come from environment variables, well-known file locations, and cloud metadata services.

### 3. Fail Fast, Fail Clearly
Errors should tell you exactly what went wrong: which provider, which operation, which credential, and how to fix it.

### 4. Testable Everything
Every operation should be mockable. Aether includes an in-memory provider for local development and CI.

### 5. Extensible
Adding a new cloud provider should be implementing interfaces and registering them. Users can also create custom providers.

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                 Your Application                 │
│         (uses aether-core interfaces)            │
├─────────────────────────────────────────────────┤
│                   aether-core                    │
│  ┌──────────┬──────────┬──────────┬──────────┐  │
│  │ BlobStore│ Compute  │ Messaging│ Secrets  │  │
│  │ Interface│ Interface│ Interface│ Interface│  │
│  └──────────┴──────────┴──────────┴──────────┘  │
│         Provider Registry & Config               │
├─────────────────────────────────────────────────┤
│              Provider Implementations             │
│  ┌──────────┬──────────┬──────────┐              │
│  │aether-aws│aether-gcp│aether-   │              │
│  │          │          │azure     │              │
│  └──────────┴──────────┴──────────┘              │
├─────────────────────────────────────────────────┤
│           Native Cloud SDKs (transitive)          │
│  ┌──────────┬──────────┬──────────┐              │
│  │ AWS SDK  │ GCP SDK  │ Azure SDK│              │
│  └──────────┴──────────┴──────────┘              │
└─────────────────────────────────────────────────┘
```

---

## Module Structure

| Module | Purpose |
|--------|---------|
| `aether-core` | Interfaces, config, provider registry, common utilities |
| `aether-aws` | AWS implementations (S3, EC2, SQS, Secrets Manager, etc.) |
| `aether-gcp` | GCP implementations (GCS, Compute Engine, Pub/Sub, etc.) |
| `aether-azure` | Azure implementations (Blob Storage, VMs, Service Bus, etc.) |

---

## Roadmap

### Phase 1: Foundation (Current)
- [x] Multi-module Gradle project with convention plugins
- [x] Centralized dependency management
- [ ] Core interfaces (`BlobStore`, `ComputeEngine`, etc.)
- [ ] Configuration system (YAML, env vars, secrets)
- [ ] Provider registry and lifecycle

### Phase 2: First Provider
- [ ] AWS `S3BlobStore` implementation
- [ ] Integration tests against LocalStack
- [ ] In-memory `BlobStore` for testing

### Phase 3: Expand Coverage
- [ ] GCP `GcsBlobStore` implementation
- [ ] Azure `AzureBlobStore` implementation
- [ ] Messaging interfaces and implementations
- [ ] Secrets management interfaces and implementations

### Phase 4: Production Ready
- [ ] Retry logic with configurable policies
- [ ] Metrics and observability (Micrometer integration)
- [ ] Structured logging
- [ ] Comprehensive documentation and examples
- [ ] CI/CD pipeline

### Phase 5: Ecosystem
- [ ] Spring Boot starter
- [ ] Quarkus extension
- [ ] Micronaut integration
- [ ] CLI tool for scaffolding and diagnostics

---

## Comparison to Existing Tools

| Tool | What It Does | How Aether Differs |
|------|-------------|-------------------|
| **Terraform** | Infrastructure provisioning (IaC) | Aether is a runtime library, not IaC |
| **Apache jclouds** | Multi-cloud library (older, less maintained) | Aether is modern, Kotlin-friendly, with better DX |
| **Spring Cloud AWS** | AWS integration for Spring | Aether is framework-agnostic and multi-cloud |
| **Crossplane** | Kubernetes-native cloud resource management | Aether is application-level, not cluster-level |

---

## License

Apache 2.0 (planned)
