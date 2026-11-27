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

CI jobs use the same mirrors. The `setup-mirrors` action (`.github/actions/setup-mirrors/`) sets the `XROAD_MIRROR_*` variables for the rest of the job. Workflows pass the secret and the three repository variables below to the action as the inputs `token`, `base-url`, `username` and `disabled`.

### Repository secret and variables

The token is the only repository secret (Settings, Secrets and variables, Actions, tab Secrets). The base URL, the user name and the off switch are repository variables (same page, tab Variables). `<host>` is the host name of the Artifactory instance.

| Name | Kind | Value shape | Used for |
|---|---|---|---|
| `XROAD_MIRROR_TOKEN` | Secret | Access token | The only credential, for every mirror |
| `XROAD_MIRROR_BASE_URL` | Variable | `https://<host>/artifactory`, with or without a trailing slash | Artifactory base URL. The action derives all per-tool URLs from it |
| `XROAD_MIRROR_USERNAME` | Variable | User name of the CI account | Login name that goes with the token |
| `XROAD_MIRRORS_DISABLED` | Variable | `true` or empty | Off switch (see below) |

The action derives the per-tool URLs from the base URL with fixed repository keys and exports them with the same names as the variables in `~/.gradle/gradle.properties`:

| Exported variable | Value | Used for |
|---|---|---|
| `XROAD_MIRROR_MAVEN_URL` | `https://<host>/artifactory/mirror-mavencentral/` | Gradle dependency repository (a Maven repository URL) |
| `XROAD_MIRROR_PLUGINS_URL` | `https://<host>/artifactory/mirror-gradle-plugins/` | Gradle plugin repository (a Maven repository URL) |
| `XROAD_MIRROR_NPM_URL` | `https://<host>/artifactory/api/npm/mirror-npm/` | npm registry. The build writes the token under the host and path of this URL |
| `XROAD_MIRROR_UBUNTU_URL` | `https://<host>/artifactory/mirror-ubuntu-ports/` | APT sources and the DNF base (see below) |
| `XROAD_MIRROR_DOCKER_URL` | `<host>/mirror-docker-hub/` | Base image registry prefix: no scheme, trailing slash. The build puts it in front of the image name. The login uses the part before the first `/` |
| `XROAD_MIRROR_GITHUB_URL` | `https://<host>/artifactory/mirror-github/` | GitHub release downloads. The build appends the repository path to it |
| `XROAD_MIRROR_K8S_URL` | `https://<host>/artifactory/mirror-k8s-dl/` | `kubectl` downloads. The build appends `/release/<version>/...` |

The Ubuntu URL has two more roles:

- The APT configuration replaces the `mirror-ubuntu...` path segment with `mirror-ubuntu-archive` on amd64 and with `mirror-ubuntu-ports` on arm64. Both repositories must exist.
- The RPM builder images use the part of the URL before the `/mirror-` path segment as the base of the DNF repositories. The repositories `mirror-rocky`, `mirror-oracle` and `mirror-epel` must exist under that base.

The mirrors are off for a job when `XROAD_MIRROR_TOKEN` is empty. When the token is set but `XROAD_MIRROR_USERNAME` or `XROAD_MIRROR_BASE_URL` is empty, or the base URL does not start with `http://` or `https://`, the mirrors stay off and the job logs a warning that names the variable, not its value.

### Credential and non-credential values

Only the token is a credential. Keep it out of images, logs, files in the repository and chat.

The mirror URL and the CI username are not credentials. The image scripts print them in the build output, and they can appear in public image history (`docker history`) because the Dockerfiles take them as build arguments. They are repository variables, so GitHub does not mask them. They appear unmasked in public workflow logs, which makes mirror problems easy to debug. Without the token, they give no access.

### Required remote repositories

Create these remote repositories on the Artifactory instance. Use the names from the URLs above.

| Repository | Type | Upstream |
|---|---|---|
| `mirror-mavencentral` | Maven | Maven Central |
| `mirror-gradle-plugins` | Maven | Gradle plugin portal |
| `mirror-npm` | npm | npm registry |
| `mirror-ubuntu-archive` | Debian | Ubuntu archive (amd64) |
| `mirror-ubuntu-ports` | Debian | Ubuntu ports (arm64) |
| `mirror-rocky` | Generic or RPM | Rocky Linux repositories |
| `mirror-oracle` | Generic or RPM | Oracle Linux repositories |
| `mirror-epel` | Generic or RPM | EPEL repositories |
| `mirror-docker-hub` | Docker | Docker Hub |
| `mirror-github` | Generic | github.com (release downloads) |
| `mirror-k8s-dl` | Generic | dl.k8s.io |

### Token scope and rotation

- Give the CI token Read and Deploy/Cache on these remote repositories only. Deploy/Cache lets Artifactory cache a first-time download; it does not allow uploads. The token needs no delete, annotate or admin rights.
- Use a group-scoped token for a dedicated non-human user. Its user name must equal `XROAD_MIRROR_USERNAME`.
- To rotate the token: create the new token, replace the `XROAD_MIRROR_TOKEN` repository secret, check one CI run, then revoke the old token.
- Developers keep their own token in `~/.gradle/gradle.properties`. They do not share the CI token.
- In the build and package job, the layer scan runs after the images are pushed and after the e2e jobs are told the images are ready, so it only detects a leak. If it reports the token, rotate the token and delete the image tags of that run.

### Off switch and outage recovery

To turn the mirrors off for all CI jobs, set the repository variable `XROAD_MIRRORS_DISABLED` to `true`. Jobs then use the public sources. The check ignores letter case and surrounding spaces. Delete the variable or set it to `false` to turn the mirrors on again.

When the mirror is down and the mirrors are on, builds fail. They do not fall back to the public sources. To recover, set `XROAD_MIRRORS_DISABLED` to `true`, re-run the failed jobs, and delete the variable when the mirror works again.

### Dockerfile rules

These rules keep the token out of image layers. They apply to every Dockerfile that sets up the APT or DNF mirror with `configure-mirror-apt.sh` or `configure-mirror-dnf.sh`. Examples: `deployment/native-packages/docker/deb-resolute/Dockerfile`, `deployment/security-server/images/base-images/auxiliary-service/Dockerfile` (APT) and `deployment/native-packages/docker/rpm-el9/Dockerfile` (DNF).

- **Secrets.** The build scripts pass two BuildKit secrets, and only when the user, the token and at least one mirror URL are set: `mirror_netrc` (netrc lines `machine <mirror host> login <user> password <token>`, one per distinct host of the APT, GitHub and kubectl mirror URLs, built in the environment variable `XROAD_MIRROR_NETRC`) and, for RPM builds, `mirror_token` (the bare token). A secret mount exists only while its `RUN` runs, so no layer holds it. No `RUN` writes or deletes a credential file.
- **APT.** Every `RUN` that runs `apt-get` or `apt` mounts `mirror_netrc` at the apt login file: `--mount=type=secret,id=mirror_netrc,target=/etc/apt/auth.conf.d/mirror.conf`. The configure step mounts `mirror_netrc` at its default path `/run/secrets/mirror_netrc` and uses it only to see that the mirror is set up; it writes no credential file.
- **DNF.** The repository files that `configure-mirror-dnf.sh` writes hold `username=<user>` and the literal `password=$mirror_token`. dnf reads the value of `$mirror_token` from `/etc/dnf/vars/mirror_token`, so every `RUN` that runs dnf with the mirror configured mounts the secret there: `--mount=type=secret,id=mirror_token,target=/etc/dnf/vars/mirror_token`. The configure step also mounts `mirror_netrc` for the GPG key downloads. Configure, install and cleanup (`configure-mirror-dnf.sh`, `dnf install`, `dnf clean all`, `cleanup-mirror-dnf.sh`) stay in one `RUN`, so the repository files do not stay in an earlier layer.
- **No secret.** `required` is off for the mounts. A build without the secret gets no mount, and the configure step prints "Skipping mirror setup".
- **Ansible and LXD.** They have no BuildKit secret. They run the same configure scripts with the token in `XROAD_MIRROR_TOKEN`, and the scripts write the credential files and keep them.
- **Cleanup.** The last mirror step of an APT image runs `cleanup-mirror-apt.sh`, so the published image uses the public sources.
- **Download steps.** A `RUN` that downloads a file mounts `mirror_netrc` and passes it with `curl --netrc-file /run/secrets/mirror_netrc`, only when its mirror URL build argument is set (`GITHUB_URL`, `KUBECTL_DIST_URL`) and the secret file is not empty. Otherwise the step downloads from the public URL without credentials. curl matches the netrc `machine` by host name and ignores the port, so a mirror URL with a port works for apt but not for curl. Examples: `deployment/security-server/images/base-images/baseline/Dockerfile` and `deployment/security-server/images/base-images/auxiliary-service/Dockerfile`.

The layer scan `scripts/images/test-mirror-layer-leak.sh` enforces these rules on the images that CI builds. It fails when a layer or the image configuration holds the token, or when the final filesystem keeps the APT TLS-verification override. The scan runs only when the mirrors are on, that is, in same-repository runs. Fork and Dependabot runs have no token, so they have no scan. To scan a local image, run `XROAD_MIRROR_TOKEN=<token> scripts/images/test-mirror-layer-leak.sh <image>`.

### Fork and Dependabot builds

GitHub gives no repository secrets to pull requests from forks. Dependabot runs get only Dependabot secrets. These builds have no mirror token, so they use public sources.

The weekly dependency audit does not use the mirrors. It can audit older branches that do not have the mirror setup.
