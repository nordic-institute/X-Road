# Developer Setup Guide: Artifactory Mirrors

The X-Road build system supports optional Artifactory mirrors for all external dependencies (Maven, Gradle plugins, npm, Docker Hub, APT/DNF, GitHub releases, kubectl). Mirrors are opt-in — when configured, builds use Artifactory; otherwise they use public registries directly.

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
XROAD_MIRROR_NPM_URL=https://artifactory.niis.org/artifactory/api/npm/mirror-npm/
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

### 4. Log-in to docker registry

```bash
docker login artifactory.niis.org -u <your-username> -p <your-artifactory-token>
```

## Disabling Mirrors

- **Gradle:** remove or comment out the properties from `~/.gradle/gradle.properties`
- **Docker builds:** pass the `--no-mirror` flag
- **Temporarily:** unset individual env vars, e.g. `unset XROAD_MIRROR_MAVEN_URL`

## GitHub Actions

CI jobs use the same mirrors. The `setup-mirrors` action (`.github/actions/setup-mirrors/`) sets the `XROAD_MIRROR_*` variables for the rest of the job.

### Repository secrets

Set these repository secrets. They have the same names and meaning as the variables in `~/.gradle/gradle.properties`:

- `XROAD_MIRROR_TOKEN`
- `XROAD_MIRROR_USERNAME`
- `XROAD_MIRROR_MAVEN_URL`
- `XROAD_MIRROR_PLUGINS_URL`
- `XROAD_MIRROR_NPM_URL`
- `XROAD_MIRROR_UBUNTU_URL`
- `XROAD_MIRROR_DOCKER_URL`
- `XROAD_MIRROR_GITHUB_URL`
- `XROAD_MIRROR_K8S_URL`

The mirrors are off for a job when `XROAD_MIRROR_TOKEN` is empty.

### Off switch

To turn the mirrors off for all CI jobs, set the repository variable `XROAD_MIRRORS_DISABLED` to `true`. Jobs then use the public sources. The check ignores letter case and surrounding spaces. Delete the variable or set it to `false` to turn the mirrors on again.

### Fork and Dependabot builds

GitHub gives no repository secrets to pull requests from forks. Dependabot runs get only Dependabot secrets. These builds have no mirror token, so they use public sources.

The weekly dependency audit does not use the mirrors. It can audit older branches that do not have the mirror setup.
