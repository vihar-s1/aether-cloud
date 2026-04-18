# Getting Started with Aether

Aether is a multi-cloud abstraction framework for Java. This guide walks you through setting up your first cloud-agnostic application in minutes.

## Prerequisites

- Java 21+
- Gradle 8+ (or use the wrapper: `./gradlew`)
- An account on at least one cloud provider (AWS, GCP, or Azure), or use the in-memory provider for local development

## Project Setup

Add the Aether modules you need to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core abstractions (always required)
    implementation("io.foundry.aether:aether-core:0.1.0")

    // Pick your cloud provider(s)
    implementation("io.foundry.aether:aether-aws:0.1.0")     // AWS
    // implementation("io.foundry.aether:aether-gcp:0.1.0")  // GCP
    // implementation("io.foundry.aether:aether-azure:0.1.0")// Azure

    // In-memory provider for tests — no cloud account required
    testImplementation("io.foundry.aether:aether-inmemory:0.1.0")
}
```

## Your First 5 Minutes

### 1. Create a Provider

Every Aether session starts with a `CloudProvider`. It holds credentials and manages lifecycle.

```java
// AWS Provider
var provider = new AwsCloudProvider(
    System.getenv("AWS_ACCESS_KEY_ID"),
    System.getenv("AWS_SECRET_ACCESS_KEY"),
    null,          // endpoint (null = standard AWS)
    "us-east-1"   // region
);
provider.initialize();
```

For local development without a cloud account, use `InMemoryCloudProvider`:

```java
var provider = new InMemoryCloudProvider();
provider.initialize();
```

### 2. Create a Service

Services are created per-provider and work through the same Aether interfaces regardless of which cloud you're on:

```java
// AWS
var blobStore = new AwsS3BlobStore(awsProvider);

// InMemory (exact same interface)
var blobStore = new InMemoryBlobStore(inMemoryProvider);
```

### 3. Upload Your First Blob

```java
byte[] data = "Hello, Cloud!".getBytes();
var request = UploadBlobRequest.of("my-bucket", "hello.txt", data, "text/plain");
var metadata = blobStore.upload(request);

System.out.println("Uploaded: " + metadata.key());
System.out.println("Size: " + metadata.sizeBytes() + " bytes");
```

### 4. Download It Back

```java
var ref = new BlobRef("my-bucket", "hello.txt");
try (var content = blobStore.download(ref)) {
    String text = new String(content.data().readAllBytes());
    System.out.println("Downloaded: " + text);  // "Hello, Cloud!"
}
```

### 5. Handle Errors

All operations throw `CloudException` (unchecked) and its subclasses:

```java
try {
    blobStore.download(new BlobRef("my-bucket", "missing.txt"));
} catch (ResourceNotFoundException e) {
    System.out.println("File not found: " + e.getMessage());
} catch (AuthenticationException e) {
    System.out.println("Check your credentials");
} catch (ProviderUnavailableException e) {
    System.out.println("Cloud service temporarily down — retryable: " + e.retryable());
}
```

## Complete Example: File Upload Service

```java
import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.storage.AwsS3BlobStore;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.storage.*;

public class FileUploadService {

    private final BlobStore blobStore;
    private final String bucket;

    public FileUploadService(BlobStore blobStore, String bucket) {
        this.blobStore = blobStore;
        this.bucket = bucket;
    }

    public String upload(String filename, byte[] data, String contentType) {
        var request = UploadBlobRequest.of(bucket, filename, data, contentType);
        var metadata = blobStore.upload(request);
        return metadata.key();
    }

    public byte[] download(String filename) {
        try (var content = blobStore.download(new BlobRef(bucket, filename))) {
            return content.data().readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read downloaded content", e);
        }
    }

    // Factory method for different environments
    public static FileUploadService forAws(String bucket) {
        var provider = new AwsCloudProvider(
            System.getenv("AWS_ACCESS_KEY_ID"),
            System.getenv("AWS_SECRET_ACCESS_KEY"),
            null,
            System.getenv("AWS_REGION")
        );
        provider.initialize();
        return new FileUploadService(new AwsS3BlobStore(provider), bucket);
    }

    public static FileUploadService forTesting(String bucket) {
        var provider = new io.foundry.aether.inmemory.InMemoryCloudProvider();
        provider.initialize();
        return new FileUploadService(
            new io.foundry.aether.inmemory.storage.InMemoryBlobStore(provider), bucket);
    }
}
```

Testing becomes trivial because the interface is identical:

```java
@Test
void uploadAndRetrieve() {
    // Uses in-memory, no AWS account needed
    var service = FileUploadService.forTesting("test-bucket");

    var key = service.upload("file.txt", "hello".getBytes(), "text/plain");
    var data = service.download(key);

    assertThat(new String(data)).isEqualTo("hello");
}
```

## Provider Lifecycle

Providers go through three states:

```
INITIALIZED → RUNNING → SHUTDOWN
```

- Call `initialize()` before creating services
- Call `shutdown()` when done (typically at app shutdown)
- Providers cannot be reused after `shutdown()`

## Secret Management Example

```java
var secretManager = new AwsSecretsManager(provider);

// Store a secret
secretManager.createSecret("database/password", "s3cr3t!");

// Retrieve it
var secret = secretManager.getSecret("database/password");
System.out.println("Password: " + secret.value());

// Rotate to a new version (value unchanged, version bumped)
var rotated = secretManager.rotate("database/password");
System.out.println("New version: " + rotated.versionId());
```

## Compute Example

```java
var engine = new AwsEc2ComputeEngine(provider);

// Launch a VM
var config = new InstanceConfig("web-server", "t2.micro", "ami-12345", "us-east-1", Map.of());
var instance = engine.createInstance(config);
System.out.println("Running: " + instance.publicIp());

// Terminate when done
engine.terminateInstance(instance.instanceId());
```

## Next Steps

- 📖 [AWS Provider Guide](./providers/aws.md)
- 📖 [In-Memory Provider (Testing)](./providers/inmemory.md)
- 📖 [NFS Provider (Local Dev)](./providers/nfs.md)
- 🏗️ [Architecture Overview](./architecture.md)
