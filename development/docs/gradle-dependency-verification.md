# Gradle Dependency Verification

SHA-256 checksums in `core/src/gradle/verification-metadata.xml` pin every dependency for reproducible builds.

## Updating after a dependency change

After bumping a version in `libs.versions.toml` or adding a dependency:

```bash
bash core/development/docs/regenerate-verification-metadata.sh
```

The orchestrator runs:

1. Drops all `<component>` entries from `verification-metadata.xml` (keeps `<configuration>` — trust rules, verify flags).
2. Runs `./gradlew --write-verification-metadata sha256 build` to regenerate entries for every resolved dependency on the host platform.
3. Fills OS-classifier gaps for cross-platform native artifacts (protoc, grpc, netty).

Commit the resulting `verification-metadata.xml`.

## Troubleshooting

- **Gradle step fails** → metadata already cleared. Restore with `git checkout src/gradle/verification-metadata.xml` before retry.
- **Build verifies on your host but fails on Linux/Windows CI** → OS-classifier entries missing. Re-run the orchestrator, or just the gap-filler:
  ```bash
  bash core/development/docs/update-verification-metadata.sh
  ```
- **`HTTP 404` lines** during gap-fill: artifact not published for that platform — expected and skipped.

## CI

Dependabot auto-updates run `.github/workflows/dependabot-gradle-metadata.yml`, which calls the same orchestrator with a faster `dependencies` task.

## Further Reading

- https://docs.gradle.org/current/userguide/dependency_verification.html
