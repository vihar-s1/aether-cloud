# Aether Architecture: Multi-Cloud Abstraction

Aether provides a unified, cloud-agnostic API for multi-cloud applications. Write business logic once, deploy to AWS, GCP, Azure without code changes.

## Core Philosophy

```mermaid
graph TB
    A["Your Application<br/>Business Logic"]
    
    B[Aether Interfaces BlobStore, ComputeEngine, SecretManager]
    
    C[AWS Provider]
    D[GCP Provider]
    E[Azure Provider]
    
    F[Real AWS Services]
    G[Real GCP Services]
    H[Real Azure Services]
    
    A -->|Uses| B
    
    B -->|Implements| C
    B -->|Implements| D
    B -->|Implements| E
    
    C -->|Calls| F
    D -->|Calls| G
    E -->|Calls| H
    
    style A fill:#4CAF50,color:#fff
    style B fill:#2196F3,color:#fff
    style C fill:#FF9800,color:#fff
    style D fill:#4285F4,color:#fff
    style E fill:#0078D4,color:#fff
    style F fill:#FF9800,color:#fff
    style G fill:#4285F4,color:#fff
    style H fill:#0078D4,color:#fff
```

**Key Insight**: Your application depends on Aether interfaces, not cloud SDKs. Swap providers at runtime with zero business logic changes.

## Service Interfaces

### BlobStore

Unified API for object/blob storage.

| Operation | Purpose |
|---|---|
| `upload(request)` | Store a file/object |
| `download(ref)` | Retrieve a file/object |
| `list(request)` | List objects with prefix filtering |
| `getMetadata(ref)` | Get size, content-type, modified time |
| `exists(ref)` | Check if object exists |
| `delete(ref)` | Remove an object |

**Implementations**:
- 🟠 AWS: `AwsS3BlobStore` → S3
- 🔵 GCP: `GcsBlobStore` → Cloud Storage
- 🔷 Azure: `AzureBlobStoreService` → Blob Storage

### ComputeEngine

Unified API for virtual machine provisioning.

| Operation | Purpose |
|---|---|
| `createInstance(config)` | Launch a VM |
| `getInstance(id)` | Get instance details |
| `listInstances()` | List all instances |
| `terminateInstance(id)` | Stop and delete a VM |

**Implementations**:
- 🟠 AWS: `AwsEc2ComputeEngine` → EC2
- 🔵 GCP: `GceComputeEngine` → Compute Engine
- 🔷 Azure: `AzureVmComputeEngine` → Virtual Machines

### SecretManager

Unified API for credential/secret storage with versioning.

| Operation | Purpose |
|---|---|
| `createSecret(id, value)` | Store a secret |
| `getSecret(id)` | Retrieve a secret by id |
| `updateSecret(id, value)` | Change secret value |
| `rotate(id)` | Generate new version (user owns rotation logic) |
| `deleteSecret(id)` | Remove a secret |
| `listSecrets()` | List all secrets |

**Implementations**:
- 🟠 AWS: `AwsSecretsManager` → Secrets Manager
- 🔵 GCP: `GcpSecretManager` → Secret Manager
- 🔷 Azure: `AzureKeyVaultSecretManager` → Key Vault

## Module Structure

```
aether/
├── aether-core/           ← Interfaces, exceptions, models
├── aether-aws/            ← AWS provider (S3, EC2, Secrets Manager)
├── aether-gcp/            ← GCP provider (Cloud Storage, Compute Engine, Secret Manager)
├── aether-azure/          ← Azure provider (Blob Storage, Virtual Machines, Key Vault)
├── aether-inmemory/       ← Test implementations (in-memory, thread-safe)
└── aether-nfs/            ← Filesystem-based provider for local development
```

## Exception Hierarchy

All Aether operations throw unchecked `CloudException` and subclasses:

```java
CloudException (unchecked RuntimeException)
├── ResourceNotFoundException        // 404 - resource not found
│   └── BlobNotFound, InstanceNotFound, SecretNotFound
│
├── AuthenticationException          // 401/403 - invalid credentials
│   └── InvalidApiKey, ExpiredToken
│
├── ProviderUnavailableException    // 503/429 - service degradation
│   └── RateLimitExceeded, ServiceDown
│
└── GenericCloudException           // all others
    └── MalformedResponse, UnexpectedError
```

**Benefit**: Catch provider-independent exceptions. Switch providers without changing error handling.

## Provider Comparison

### Lighthearted Feature Matrix 😄

```mermaid
graph LR
    A["Feature"]
    
    B["🟠 AWS"]
    C["🔵 GCP"]
    D["🔷 Azure"]
    
    E1["Maturity"]
    E2["🏆 Ancient"]
    E3["📈 Solid"]
    E4["🌱 Growing"]
    
    F1["Pricing"]
    F2["💰 Per millisecond"]
    F3["🤑 Reserved instances"]
    F4["💳 Enterprise deals"]
    
    G1["Documentation"]
    G2["📚 Infinite Stack Overflow"]
    G3["🎓 Beautiful APIs"]
    G4["🔍 Enterprise support"]
    
    A --> E1
    E1 --> E2
    E1 --> E3
    E1 --> E4
    
    A --> F1
    F1 --> F2
    F1 --> F3
    F1 --> F4
    
    A --> G1
    G1 --> G2
    G1 --> G3
    G1 --> G4
    
    style E2 fill:#FF9800,color:#fff
    style E3 fill:#4285F4,color:#fff
    style E4 fill:#0078D4,color:#fff
    
    style F2 fill:#FF9800,color:#fff
    style F3 fill:#4285F4,color:#fff
    style F4 fill:#0078D4,color:#fff
    
    style G2 fill:#FF9800,color:#fff
    style G3 fill:#4285F4,color:#fff
    style G4 fill:#0078D4,color:#fff
```

### Real Comparison

| Aspect | AWS | GCP | Azure |
|---|---|---|---|
| **Market Share** | 32% 🏆 | 11% | 23% 📈 |
| **Blob Storage** | S3 (industry standard) | Cloud Storage | Blob Storage |
| **Compute** | EC2 (diverse options) | Compute Engine | Virtual Machines |
| **Secrets** | Secrets Manager | Secret Manager | Key Vault |
| **Regional Availability** | 33 regions | 40 regions | 60 regions |
| **Free Tier** | 12 months | Perpetual | 12 months |

## Data Flow Example: File Upload

Uploading a file through Aether across different providers:

```mermaid
sequenceDiagram
    participant App as Your Application
    participant Aether as Aether BlobStore
    participant AWS as AWS S3
    participant GCP as GCP Cloud Storage
    participant Azure as Azure Blob Storage
    
    App ->> Aether: upload(bucket, key, data)
    
    alt Provider = AWS
        Aether ->> AWS: PutObject
        AWS -->> Aether: Success (ETag)
    else Provider = GCP
        Aether ->> GCP: CreateObject
        GCP -->> Aether: Success (generation)
    else Provider = Azure
        Aether ->> Azure: PutBlob
        Azure -->> Aether: Success (content-md5)
    end
    
    Aether -->> App: BlobMetadata(bucket, key, size, type, modified)
```

**Key Point**: Your app calls `.upload()` once. Aether routes to the right cloud provider. No conditional logic needed.

## Provider Registration & Lookup

```java
// Register a provider (typically at app startup)
var provider = new AwsCloudProvider(key, secret, null, "us-east-1");
provider.initialize();
DefaultProviderRegistry.instance().register(provider);

// Lookup a service by provider name
var blobStore = DefaultProviderRegistry.instance()
    .getService("aws", BlobStore.class);
```

## Service Initialization Pattern

```mermaid
graph TB
    A["Create Provider<br/>new AwsCloudProvider(...)"]
    B["Initialize Provider<br/>provider.initialize()"]
    C["Register Provider<br/>registry.register(provider)"]
    D["Get Service<br/>registry.getService(..., BlobStore.class)"]
    E["Use Service<br/>blobStore.upload(...)"]
    
    A --> B
    B --> C
    C --> D
    D --> E
    
    style A fill:#E3F2FD
    style B fill:#E3F2FD
    style C fill:#FFF3E0
    style D fill:#F3E5F5
    style E fill:#C8E6C9
```

## Error Recovery Strategy

Aether encourages resilience through exception handling:

```mermaid
graph TB
    A["CloudException"]
    
    B["ResourceNotFoundException"]
    C["AuthenticationException"]
    D["ProviderUnavailableException"]
    E["GenericCloudException"]
    
    F["Fail Fast<br/>✗ File not found"]
    G["Fail Fast<br/>✗ Invalid creds"]
    H["Retry with Backoff<br/>⟳ Service recovering"]
    I["Log & Escalate<br/>? Unknown error"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    B --> F
    C --> G
    D --> H
    E --> I
    
    style F fill:#ffcccc
    style G fill:#ffcccc
    style H fill:#ffffcc
    style I fill:#ffccff
```

## Testing Strategies

### Unit Tests with Mock Providers
Use `aether-inmemory` for fast, deterministic tests:

```java
@Test
void testUploadAndDownload() {
    var provider = new InMemoryCloudProvider();
    var blobStore = new InMemoryBlobStore(provider);
    
    // Test doesn't call AWS
    var metadata = blobStore.upload(request);
    var content = blobStore.download(new BlobRef(...));
    
    assertThat(content.data()).isEqualTo(originalData);
}
```

### Integration Tests with Real Cloud
Test against actual AWS/GCP/Azure:

```java
@Test
@SkipIfEnvMissing("AWS_ACCESS_KEY_ID")
void testRealS3Upload() {
    var provider = new AwsCloudProvider(...);
    var blobStore = new AwsS3BlobStore(provider);
    
    var metadata = blobStore.upload(realRequest);
    // Cleans up after test
}
```

### Contract Tests
Verify all providers implement the same interface:

```java
@ParameterizedTest
@ValueSource(classes = {
    AwsCloudProvider.class,
    GcpCloudProvider.class,
    AzureCloudProvider.class
})
void allProvidersUploadDownload(Class<?> providerClass) {
    // Same test, different provider
    // Proves interchangeability
}
```

## Next Steps

- 📖 [AWS Provider Guide](./providers/aws.md)
- 📖 [GCP Provider Guide](./providers/gcp.md)
- 📖 [Azure Provider Guide](./providers/azure.md)
- 🧪 [Testing Guide](./testing.md)
- ⚙️ [Configuration Guide](./configuration.md)
