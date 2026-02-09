# Developer Setup Guide: Artifactory Mirrors

The X-Road build system supports optional Artifactory mirrors for all external dependencies (Maven, Gradle plugins, npm, Docker Hub, APT/YUM, GitHub releases, kubectl). Mirrors are opt-in — when configured, builds use Artifactory; otherwise they use public registries directly.

## Prerequisites

- Access to `artifactory.niis.org`
- Personal Artifactory token (generate from Artifactory UI → User Profile → Generate Token)

## Quick Setup (3 steps)

### 1. Add mirror config to `~/.gradle/gradle.properties`

Add the following to your **global** Gradle properties file (`~/.gradle/gradle.properties`):

```properties
# X-Road Artifactory Mirrors
XROAD_MIRROR_TOKEN=<your-artifactory-token>
XROAD_MIRROR_USERNAME=<your-username>
XROAD_MIRROR_MAVEN_URL=https://artifactory.niis.org/artifactory/mirror-mavencentral/
XROAD_MIRROR_PLUGINS_URL=https://artifactory.niis.org/artifactory/mirror-gradle-plugins/
XROAD_MIRROR_NPM_URL=https://artifactory.niis.org/artifactory/mirror-npm/
XROAD_MIRROR_UBUNTU_URL=https://artifactory.niis.org/artifactory/mirror-ubuntu-ports/
XROAD_MIRROR_DOCKER_URL=artifactory.niis.org/mirror-docker-hub/
XROAD_MIRROR_K8S_URL=https://artifactory.niis.org/artifactory/mirror-k8s-dl/
XROAD_MIRROR_GITHUB_URL=https://artifactory.niis.org/artifactory/mirror-github/
```

### 2. Source the helper script in your shell

Add to `~/.zshrc` (or `~/.bashrc`):

```bash
# X-Road Artifactory mirrors (reads from ~/.gradle/gradle.properties)
source <path-to-x-road>/development/.scripts/setup-dev-xrd-mirrors.sh
```

Replace `<path-to-x-road>` with the absolute path to your X-Road repository checkout.

### 3. Restart shell and verify

```bash
env | grep XROAD_MIRROR | grep -v TOKEN
```

You should see all mirror URLs listed (token is excluded for security).

## Disabling Mirrors

- **Gradle:** remove or comment out the properties from `~/.gradle/gradle.properties`
- **Docker builds:** pass the `--no-mirror` flag
- **Temporarily:** unset individual env vars, e.g. `unset XROAD_MIRROR_MAVEN_URL`
