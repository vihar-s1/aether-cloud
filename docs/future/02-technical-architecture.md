# Technical Architecture & Design Decisions

## Build System

### Why Kotlin DSL over Groovy?
- Type safety at compile time
- Better IDE support (autocomplete, refactoring)
- Gradle's recommended direction
- Consistent with convention plugins (which must be Kotlin/Java)

### Why Multi-Module?
- Cloud SDKs are heavy (50MB+ each). Users shouldn't pull in Azure if they only use AWS.
- Independent versioning and release cycles per provider
- Clear separation of concerns

### Why BuildSrc?
- Centralized convention plugin avoids copy-pasting build config
- Single source of truth for dependency versions
- Can migrate to composite build later if cross-project reuse is needed

---

## Dependency Strategy

### Version Management
All versions live in `buildSrc/.../AetherVersions.kt`. One file, every module reads from it.

### BOM Usage
- AWS SDK BOM — ensures all AWS SDK modules use compatible versions
- GCP Libraries BOM — same for Google Cloud libraries
- Azure — individual artifacts (no official BOM)

### Transitive Dependencies
Cloud SDKs are declared as `api` in provider modules, meaning they're exposed to consumers. This lets users access native SDK features when needed without adding the dependency themselves.

---

## Java Version

**Target: Java 21** (LTS)

Rationale:
- Virtual threads (Project Loom) — useful for async cloud operations
- Pattern matching, records, sealed classes — cleaner API design
- LTS means long support window for enterprise users
- Most cloud providers support Java 21 natively

---

## Code Organization (Planned)

```
aether-core/
└── io.foundry.aether
    ├── core/           # Base types: CloudProvider, CloudException
    ├── config/         # Configuration loading and resolution
    ├── storage/        # BlobStore interface
    ├── compute/        # ComputeEngine interface
    ├── messaging/      # MessageQueue interface
    ├── secrets/        # SecretManager interface
    ├── networking/     # NetworkManager interface
    └── registry/       # Provider discovery and lifecycle

aether-aws/
└── io.foundry.aether.aws
    ├── S3BlobStore
    ├── EC2ComputeEngine
    └── ...

aether-gcp/
└── io.foundry.aether.gcp
    ├── GcsBlobStore
    ├── GceComputeEngine
    └── ...

aether-azure/
└── io.foundry.aether.azure
    ├── AzureBlobStore
    ├── AzureComputeEngine
    └── ...
```

---

## Testing Strategy

### Unit Tests
- Mock all cloud SDK interactions
- Test interface contracts and error handling

### Integration Tests
- Use LocalStack for AWS, Fake GCS for GCP, Azurite for Azure
- Run against real SDKs in isolated containers

### In-Memory Providers
- `InMemoryBlobStore` — fully functional, no network
- Useful for unit testing user applications
- Ships with `aether-core`

---

## Error Handling

All cloud operations throw `CloudException` with:
- Provider name (which cloud?)
- Operation name (what were you doing?)
- Error code (provider-specific code)
- Retryable flag (should you retry?)
- Root cause (the original exception)

```java
try {
    blobStore.upload("bucket", "key", data);
} catch (CloudException e) {
    if (e.isRetryable()) {
        // retry logic
    }
    log.error("Failed to upload to {} on {}: {}", 
        e.getProvider(), e.getOperation(), e.getMessage());
}
```

---

## Configuration Design (Planned)

Configuration sources (in priority order):
1. Programmatic config (builder API)
2. Environment variables (`AETHER_PROVIDER=aws`)
3. `aether.yml` / `aether.yaml` in working directory
4. `~/.aether/config` (user-level defaults)
5. Cloud metadata service (auto-detect when running on a cloud)

---

## Decisions Log

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Build DSL | Kotlin | Groovy | Type safety, IDE support, future-proof |
| Module structure | Multi-module | Single JAR | Dependency size, selective inclusion |
| Version management | buildSrc object | Version catalog TOML | buildSrc can't read TOML, object is simpler |
| Java version | 21 | 17 | Virtual threads, records, sealed classes |
| License | Apache 2.0 | MIT, GPL | Enterprise-friendly, patent protection |
