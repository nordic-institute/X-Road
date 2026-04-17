
# Gradle Dependency Verification Guide

## Overview

X-Road uses Gradle's dependency verification to ensure secure, reproducible builds by storing SHA-256 checksums of all downloaded artifacts. This prevents tampering or mirror inconsistencies.

---

## Quick Reference: Verification Metadata Update

When you bump a version in `libs.versions.toml` or add a new dependency:

```bash
cd ../../src
./gradlew --write-verification-metadata sha256 build
```

This command:
- Resolves all dependencies (compile, runtime, test)
- Writes verification metadata for all artifacts

---

## Further Reading

- Gradle verification metadata docs: https://docs.gradle.org/current/userguide/dependency_verification.html
