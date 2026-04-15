# Development Workflow & Contribution Guidelines

## Getting Started

### Prerequisites
- Java 21 or later
- Gradle 8.14+ (wrapper included)
- Docker (for integration tests)

### Build Commands
```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run tests for a specific module
./gradlew :aether-core:test

# Clean and rebuild
./gradlew clean build

# Check code style (when Spotless is added)
./gradlew spotlessCheck

# Format code
./gradlew spotlessApply
```

---

## Adding a New Module

1. Create the module directory:
```bash
mkdir -p aether-new-module/src/main/java/io/foundry/aether/newmodule
mkdir -p aether-new-module/src/test/java/io/foundry/aether/newmodule
```

2. Add to `settings.gradle.kts`:
```kotlin
include("aether-new-module")
```

3. Create `aether-new-module/build.gradle.kts`:
```kotlin
plugins {
    id("io.foundry.aether.java-conventions")
}

description = "Description of the new module"

dependencies {
    // Module-specific dependencies
}
```

4. That's it. Convention plugin handles Java version, repos, test framework, and common deps.

---

## Adding a New Dependency

1. Add the version to `buildSrc/.../AetherVersions.kt`:
```kotlin
const val NEW_LIB = "1.0.0"
```

2. Add the coordinate to `AetherLibs`:
```kotlin
const val NEW_LIB = "com.example:new-lib:${AetherVersions.NEW_LIB}"
```

3. Use it in the appropriate module's `build.gradle.kts`:
```kotlin
dependencies {
    api(AetherLibs.NEW_LIB)  // or implementation()
}
```

---

## Coding Standards

### Naming Conventions
- Interfaces: descriptive nouns (`BlobStore`, `ComputeEngine`)
- Implementations: provider-prefixed (`S3BlobStore`, `GcsBlobStore`)
- Exceptions: `CloudException` and subclasses
- Config classes: `XxxConfig`

### Package Structure
```
io.foundry.aether.<module>
├── api/          # Public interfaces (for core)
├── impl/         # Implementations
├── config/       # Configuration classes
└── internal/     # Internal utilities (not exposed)
```

### Documentation
- Every public class and method must have a KDoc/Javadoc
- Include `@since` tag for new APIs
- Include `@see` for related classes
- Example code in `@example` blocks

### Error Messages
- Clear, actionable, and provider-aware
```
[AWS/S3] Failed to upload object 'data/file.txt' to bucket 'my-bucket': 
Access Denied. Check IAM policy for s3:PutObject permission.
```

---

## Git Workflow

### Branch Naming
- `feature/<description>` — new features
- `fix/<description>` — bug fixes
- `docs/<description>` — documentation
- `chore/<description>` — build/tooling changes

### Commit Messages
Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): description

[optional body]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Examples:
```
feat(storage): add S3BlobStore upload implementation
fix(config): resolve environment variables in YAML values
docs: add multi-cloud configuration examples
chore(deps): bump AWS SDK to 2.25.0
```

### PR Requirements
- All tests pass
- Code reviewed by at least 1 maintainer
- Documentation updated if API changed
- Commit messages follow convention

---

## Release Process (Planned)

1. Update version in `gradle.properties`
2. Update `CHANGELOG.md`
3. Tag the release: `git tag v0.1.0`
4. Publish to Maven Central (when publishing is configured)
5. Create GitHub release with notes

---

## Module Dependencies

```
aether-core          ← no aether dependencies
aether-aws           ← depends on aether-core
aether-gcp           ← depends on aether-core
aether-azure         ← depends on aether-core
```

Provider modules must **not** depend on each other. This keeps the dependency graph clean and avoids pulling in unwanted SDKs.
