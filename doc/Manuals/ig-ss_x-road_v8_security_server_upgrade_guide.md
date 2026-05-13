# Security Server Upgrade Guide: X-Road 7.8 to X-Road 8 <!-- omit in toc -->

**X-ROAD 8**

Version: 1.0
Doc. ID: IG-SS-8-UPGRADE

---

## Version history <!-- omit in toc -->

| Date       | Version | Description     | Author |
|------------|---------|-----------------|--------|
| TBD `*`    | 1.0     | Initial version | TBD `*` |

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## Table of Contents <!-- omit in toc -->

- [License](#license)
- [Overview](#overview)
  - [What this guide covers](#what-this-guide-covers)
  - [What this guide does not cover](#what-this-guide-does-not-cover)
  - [Beta 2 caveats](#beta-2-caveats)
- [Prerequisites](#prerequisites)
- [Before you start](#before-you-start)
- [Upgrade with the wizard](#upgrade-with-the-wizard)
  - [Get the wizard](#get-the-wizard)
  - [Interactive mode](#interactive-mode)
  - [Unattended mode (env vars)](#unattended-mode-env-vars)
  - [Unattended mode (config file)](#unattended-mode-config-file)
  - [What the wizard does](#what-the-wizard-does)
  - [Resuming after a failed run](#resuming-after-a-failed-run)
- [Manual upgrade procedure](#manual-upgrade-procedure)
- [Post-upgrade verification](#post-upgrade-verification)
- [Rollback](#rollback)
  - [Rollback from a snapshot (recommended)](#rollback-from-a-snapshot-recommended)
  - [Rollback by downgrading packages](#rollback-by-downgrading-packages)
- [Troubleshooting](#troubleshooting)
- [References](#references)

## Overview

This guide walks you through upgrading an existing X-Road 7.8 Security Server to X-Road 8 (beta 2 `*`) using the official upgrade wizard (`xroad-upgrade.sh`). It also documents the equivalent manual procedure for situations where the wizard cannot be used, and provides rollback guidance.

### What this guide covers

- Native-package Security Server installations (DEB on Ubuntu, RPM on RHEL/Rocky/Alma)
- Both interactive (whiptail) and unattended (env-var/config-file) execution modes
- Step-by-step manual procedure as an alternative to the wizard
- Post-upgrade verification
- Rollback options

### What this guide does not cover

- **Central Server upgrades** — covered in a separate document `*`
- **Container or Kubernetes deployments** — these follow a different upgrade path
- **Ansible-driven upgrades** — covered in a separate document `*`
- Upgrade from X-Road versions older than 7.8.x — must first be brought to 7.8.x

### Beta 2 caveats

- This procedure targets X-Road 8 beta 2. Pre-release software may behave differently from the final 8.0 release.
- Specific beta 2 disclaimers (data loss policy, supported rollback, breaking changes) `*`

## Prerequisites

Before starting, confirm the following on the Security Server:

| Requirement | Notes |
|---|---|
| Operating system | Ubuntu Server 22.04 / 24.04 LTS, or RHEL 8 / 9 / 10 (and binary-compatible Rocky / Alma) |
| Current X-Road version | 7.8.x (any patch level). Earlier versions must first be upgraded to 7.8.x. |
| PostgreSQL major version | 15 or newer. The wizard verifies this automatically. |
| Disk space | `*` — at least *N* GB free in `/var` (jar download, config backups, package upgrade) |
| Network reachability | Outbound HTTPS to the X-Road 8 package repository (`*` — exact URL TBD), the OpenBao package repository (`https://pkgs.openbao.org`), and the migration-CLI artifact URL (`*` — exact URL TBD) |
| Root / sudo access | Required for all steps |
| Backup | A current backup of the Security Server (configuration, databases, signer keys) is **strongly recommended** — see [Before you start](#before-you-start) |

Verify your current X-Road version:

```bash
# Ubuntu / Debian
dpkg-query -W -f='${Version}\n' xroad-proxy

# RHEL / Rocky / Alma
rpm -q --queryformat '%{VERSION}\n' xroad-proxy
```

The output must begin with `7.8.`.

## Before you start

1. **Take a full backup.** The upgrade is a one-way operation for some files (signer keyconf, `local.ini` → `local.yaml`). A backup is your safety net. Use the standard Security Server backup tooling (see the [Security Server User Guide](#references)) and copy the archive off the host.

2. **Notify users / pause inbound traffic.** Services will be stopped for the duration of the upgrade.

3. **Take a host snapshot if possible** (LXD, hypervisor snapshot, cloud-provider snapshot). This is the cleanest rollback path — see [Rollback](#rollback).

4. **Decide on execution mode** — interactive for hands-on upgrades, unattended for Ansible or automated runs.

5. **Have these values ready** (you will pass them to the wizard):
   - `XROAD_MIGRATION_CLI_URL` — URL to the `migration-cli.jar` artifact for X-Road 8 beta 2 `*`
   - `XROAD_REPO_BASE_URL` — base URL of the X-Road 8 package repository (default: `https://artifactory.niis.org`)
   - `XROAD_REPO_MAIN` — repository name for your distribution (`*` — exact name for beta 2 TBD, e.g. `xroad8-snapshot-deb` / `xroad8-snapshot-rpm`)
   - `OPENBAO_MIRROR` and `OPENBAO_MIRROR_USER` — only if you use a private OpenBao mirror; leave empty for the official `pkgs.openbao.org`

## Upgrade with the wizard

### Get the wizard

Obtain the X-Road installer package (which includes the upgrade wizard) from the official distribution channel `*` (release URL / Artifactory path for beta 2 TBD).

Extract it on the Security Server, for example to `/opt/xroad-installer/`:

```bash
sudo tar -xzf xroad-installer.tar.gz -C /opt/
ls /opt/xroad-installer/xroad-upgrade.sh
```

The wizard expects to find the bundled helper scripts under `/opt/xroad-installer/lib/` and the migration task scripts under `/opt/xroad-installer/tasks/migration/`.

### Interactive mode

Run the wizard as root. You will be prompted for confirmation at the version-gate and each migration-CLI step.

```bash
sudo bash /opt/xroad-installer/xroad-upgrade.sh
```

Provide the values above when prompted. To pre-set them, export them before invocation:

```bash
sudo XROAD_MIGRATION_CLI_URL='https://...migration-cli.jar' \
     XROAD_REPO_BASE_URL='https://artifactory.niis.org' \
     XROAD_REPO_MAIN='xroad8-snapshot-deb' \
     bash /opt/xroad-installer/xroad-upgrade.sh
```

### Unattended mode (env vars)

Set `XROAD_UPGRADE_UNATTENDED=true`. The wizard automatically sets `XROAD_UPGRADE_CONFIRMED=yes` and `XROAD_MIGRATION_UNATTENDED=true`, suppressing all whiptail dialogs.

```bash
sudo XROAD_UPGRADE_UNATTENDED=true \
     XROAD_MIGRATION_CLI_URL='https://...migration-cli.jar' \
     XROAD_REPO_BASE_URL='https://artifactory.niis.org' \
     XROAD_REPO_MAIN='xroad8-snapshot-deb' \
     bash /opt/xroad-installer/xroad-upgrade.sh
```

### Unattended mode (config file)

Place a configuration file with the same variables and pass it via `--config-file`:

```bash
# /etc/xroad/xroad-upgrade.conf
XROAD_UPGRADE_UNATTENDED=true
XROAD_MIGRATION_CLI_URL=https://...migration-cli.jar    # `*`
XROAD_REPO_BASE_URL=https://artifactory.niis.org
XROAD_REPO_MAIN=xroad8-snapshot-deb                     # `*` exact beta 2 repo name
XROAD_REPO_GPG_KEY_URL=                                  # default if empty
OPENBAO_MIRROR=
OPENBAO_MIRROR_USER=
XROAD_SS_PACKAGE=xroad-securityserver
```

```bash
sudo bash /opt/xroad-installer/xroad-upgrade.sh \
  --config-file /etc/xroad/xroad-upgrade.conf
```

### What the wizard does

The wizard runs nine ordered steps. Each writes a sentinel file to `/var/lib/xroad-upgrade/step-<name>.done` on success; on failure the wizard exits with a clear message and the services are left stopped.

| # | Step | What it does | Sentinel |
|---|---|---|---|
| 1 | **Version gate** | Reads installed `xroad-proxy` version; aborts unless it matches `^7\.8\.`. In interactive mode it asks for confirmation; in unattended mode it relies on `XROAD_UPGRADE_CONFIRMED=yes`. | — |
| 2 | **PostgreSQL pre-flight** | Parses `/etc/xroad/db.properties`, connects to the `serverconf` database host/port, verifies PostgreSQL major version ≥ 15. | — |
| 3 | **OpenBao repository setup** | Adds the OpenBao apt/yum repository so the 8.0 packages can pull in OpenBao dependencies. Uses the bundled `configure-mirror-openbao-{deb,rpm}.sh` helper. | — |
| 4 | **Download migration-CLI** | Fetches `migration-cli.jar` from `XROAD_MIGRATION_CLI_URL` to `/var/tmp/migration-cli.jar` (mode 600). | — |
| 5 | **Stop X-Road services** | Discovers all active `xroad-*` units via `systemctl` and stops each one, waiting up to 60 s per service to reach inactive state. | — |
| 6 | **Run migration-CLI** | Runs in order: `validate`, `config /etc/xroad/conf.d/local.ini /etc/xroad/conf.d/local.yaml`, `keyconf /etc/xroad/signer /etc/xroad/db.properties`, and `signer-token-pins` (only if `/usr/share/xroad/autologin/*-fetch-pin.sh` exists). | per sub-step |
| 7 | **Switch to V8 repository** | Backs up the existing X-Road sources file with a `.v7.bak.<timestamp>` suffix, writes the V8 sources file, imports the V8 GPG key, refreshes package metadata. | — |
| 8 | **Upgrade packages** | Runs `apt-get install -y` / `dnf install -y` for the package named by `XROAD_SS_PACKAGE` (default `xroad-securityserver`). Verifies the installed version actually changed to `8.x`; fails loudly if the install was a no-op. | — |
| 9 | **Start X-Road services** | Starts `xroad-signer`, `xroad-proxy`, `xroad-opmonitor`, `xroad-monitor` (in that order), waiting for each to reach active state. Services whose unit files are not installed (e.g. an absent op-monitor on a minimal installation) are skipped with a warning. | — |

On success the wizard prints `X-Road 8.0 upgrade completed successfully!` and the path of its log file.

### Resuming after a failed run

If the wizard fails partway through, the sentinel files under `/var/lib/xroad-upgrade/` record which migration-CLI steps already succeeded. After fixing the underlying issue, simply re-run the wizard with the same arguments — completed steps are skipped automatically.

To reset and rerun every step (e.g., after a snapshot restore):

```bash
sudo rm -rf /var/lib/xroad-upgrade
```

## Manual upgrade procedure

Use this procedure if the wizard is unavailable, or if you need to perform an unusual upgrade and want fine-grained control. The steps mirror what the wizard does internally.

Run all commands as root.

**1. Verify version.**

```bash
# Ubuntu
dpkg-query -W -f='${Version}\n' xroad-proxy
# RHEL
rpm -q --queryformat '%{VERSION}\n' xroad-proxy
```

Confirm the output begins with `7.8.`. Stop here if it does not.

**2. Verify PostgreSQL version.**

Read the serverconf JDBC URL from `/etc/xroad/db.properties` and connect:

```bash
PGPASSWORD='<serverconf-password>' \
  psql -w -h <host> -p <port> -U <serverconf-user> -d postgres \
  -tAc 'SHOW server_version_num'
```

The numeric server version (e.g. `150006` for PG 15.6) divided by 10000 must be ≥ 15.

**3. Take a backup.** Use the standard Security Server backup tooling and verify the archive exists and is readable.

**4. Configure the OpenBao package repository.**

The bundled helper script handles both APT (Ubuntu) and DNF/YUM (RHEL). Equivalent manual commands:

```bash
# Ubuntu
curl -fsSL https://openbao.org/assets/openbao-gpg-pub-20240618.asc \
  -o /usr/share/keyrings/openbao-keyring.asc
echo "deb [signed-by=/usr/share/keyrings/openbao-keyring.asc] https://pkgs.openbao.org/deb stable main" \
  > /etc/apt/sources.list.d/openbao.list
apt-get update

# RHEL
rpm --import https://openbao.org/assets/openbao-gpg-pub-20240618.asc
tee /etc/yum.repos.d/openbao.repo <<'EOF'
[openbao]
name=OpenBao
baseurl=https://pkgs.openbao.org/rpm
enabled=1
gpgcheck=1
gpgkey=https://openbao.org/assets/openbao-gpg-pub-20240618.asc
EOF
dnf makecache
```

**5. Download the migration-CLI artifact.**

```bash
curl -fsSL '<XROAD_MIGRATION_CLI_URL>' -o /var/tmp/migration-cli.jar  # `*`
chmod 600 /var/tmp/migration-cli.jar
```

**6. Stop all X-Road services.**

```bash
mapfile -t SERVICES < <(systemctl list-units --type=service --state=active 'xroad-*' \
  --no-legend --plain | awk '{print $1}')
for s in "${SERVICES[@]}"; do
  systemctl stop "$s"
  # wait up to 60 s for inactive
  for _ in {1..30}; do systemctl is-active --quiet "$s" || break; sleep 2; done
done
```

**7. Run migration-CLI steps.**

```bash
java -jar /var/tmp/migration-cli.jar validate
java -jar /var/tmp/migration-cli.jar config \
  /etc/xroad/conf.d/local.ini /etc/xroad/conf.d/local.yaml
java -jar /var/tmp/migration-cli.jar keyconf \
  /etc/xroad/signer /etc/xroad/db.properties

# Optional: only if xroad-autologin is installed
if [ -f /usr/share/xroad/autologin/custom-fetch-pin.sh ] \
   || [ -f /usr/share/xroad/autologin/default-fetch-pin.sh ]; then
  java -jar /var/tmp/migration-cli.jar signer-token-pins
fi
```

Inspect the output of each command. **Treat any Java stack trace as a failure even if the process exits 0** — do not proceed if a step printed an exception.

**8. Switch the X-Road package repository from V7 to V8.**

```bash
# Ubuntu
TS=$(date +%Y%m%d-%H%M%S)
mv /etc/apt/sources.list.d/xroad.list /etc/apt/sources.list.d/xroad.list.v7.bak."$TS"
curl -fsSL https://artifactory.niis.org/api/gpg/key/public \
  -o /usr/share/keyrings/xroad-keyring.asc
CODENAME=$(lsb_release -sc)
echo "deb [signed-by=/usr/share/keyrings/xroad-keyring.asc] https://artifactory.niis.org/<XROAD_REPO_MAIN> ${CODENAME}-current main" \
  > /etc/apt/sources.list.d/xroad.list
apt-get update

# RHEL
TS=$(date +%Y%m%d-%H%M%S)
for repo in /etc/yum.repos.d/xroad*.repo; do
  [ -f "$repo" ] && mv "$repo" "${repo}.v7.bak.${TS}"
done
dnf config-manager --add-repo "https://artifactory.niis.org/<XROAD_REPO_MAIN>"
rpm --import https://artifactory.niis.org/api/gpg/key/public
dnf makecache
```

Replace `<XROAD_REPO_MAIN>` with the beta 2 repository name `*`.

**9. Upgrade the Security Server package.**

```bash
# Ubuntu
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-securityserver

# RHEL
dnf install -y xroad-securityserver
```

Verify the version actually changed:

```bash
# Ubuntu
dpkg-query -W -f='${Version}\n' xroad-securityserver
# RHEL
rpm -q --queryformat '%{VERSION}\n' xroad-securityserver
```

The output must now begin with `8.`.

**10. Start X-Road services.**

```bash
for s in xroad-signer xroad-proxy xroad-opmonitor xroad-monitor; do
  systemctl cat "$s" >/dev/null 2>&1 || { echo "skip $s (not installed)"; continue; }
  systemctl start "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" && break; sleep 2; done
done
```

Proceed to [Post-upgrade verification](#post-upgrade-verification).

## Post-upgrade verification

**1. Check all expected services are active:**

```bash
systemctl list-units --type=service --state=active 'xroad-*'
```

You should see `xroad-signer`, `xroad-proxy`, `xroad-monitor`, and any optional services that were active before the upgrade.

**2. Confirm the package version:**

```bash
# Ubuntu
dpkg-query -W -f='${Package} ${Version}\n' 'xroad-*'
# RHEL
rpm -qa 'xroad-*'
```

Every X-Road package should be on `8.x`.

**3. Open the admin UI** at `https://<server>:4000/` and sign in. Verify clients, certificates, and global configuration are intact.

**4. Send a test request** through the Security Server (use your existing client tooling) and confirm a successful response.

**5. Inspect the upgrade log** for any warnings:

```bash
less /var/log/xroad-upgrade-*.log     # `*` confirm log location
```

`*` Add list of any specific known-good post-upgrade indicators for beta 2 (e.g. expected log line, OpenBao seal/unseal state, etc.).

## Rollback

The upgrade migrates configuration files (`local.ini` → `local.yaml`) and signer keyconf in place. Once these are migrated, simply reinstalling the 7.8 packages will **not** fully restore the previous state. The clean rollback paths are:

### Rollback from a snapshot (recommended)

If you took a host snapshot before the upgrade (LXD, hypervisor, or cloud-provider), restore it. This reverts the filesystem, services, and databases to their pre-upgrade state in one step.

```bash
# LXD example
lxc stop <container>
lxc restore <container> <pre-upgrade-snapshot-name>
lxc start <container>
```

### Rollback by downgrading packages

If no snapshot is available, you can attempt a package-level rollback. This is only partially reversible.

**1. Stop all X-Road services** (same procedure as step 6 of the manual upgrade).

**2. Restore the V7 package repository** from the backup files the wizard created:

```bash
# Ubuntu
ls /etc/apt/sources.list.d/xroad.list.v7.bak.*
mv /etc/apt/sources.list.d/xroad.list.v7.bak.<TIMESTAMP> /etc/apt/sources.list.d/xroad.list
apt-get update

# RHEL
ls /etc/yum.repos.d/xroad*.v7.bak.*
# rename the most recent backup back to its original name
dnf makecache
```

**3. Downgrade the X-Road packages** to a known-good 7.8.x version:

```bash
# Ubuntu
DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades \
  xroad-securityserver=7.8.<patch>

# RHEL
dnf downgrade -y xroad-securityserver
```

**4. Restore configuration files** from your backup:
- `/etc/xroad/conf.d/local.ini` (replaces the migrated `local.yaml`)
- `/etc/xroad/signer/*`
- Any other files modified during the upgrade

**5. Restore the serverconf, messagelog, and op-monitor databases** from your backup using the standard restore tooling.

**6. Remove upgrade sentinel files** so a future re-attempt starts fresh:

```bash
rm -rf /var/lib/xroad-upgrade
```

**7. Start the services** and re-run post-upgrade verification.

> Package-level rollback is best-effort and may leave the system in an inconsistent state. The supported rollback strategy is a snapshot restore. `*` Confirm beta 2 official rollback policy.

## Troubleshooting

**Log files:**
- Wizard log: `xroad-upgrade-<timestamp>.log` in the directory where the wizard was invoked. `*` Confirm final location convention (per-run cwd vs `/var/log/`).
- Migration-CLI output: captured in the wizard log.
- Service logs: `journalctl -u xroad-<service>` and `/var/log/xroad/*.log`.

**Sentinel files (resume after partial failure):**
```bash
ls /var/lib/xroad-upgrade/
```
Each `step-<name>.done` represents a completed migration-CLI step. Delete a specific sentinel to force that step to re-run, or delete the directory to reset everything.

**Common failure patterns:**

| Symptom | Likely cause | Resolution |
|---|---|---|
| `requires X-Road 7.8.x. Detected version: …` | Server is not on 7.8.x | Bring the server to 7.8.x first |
| `Could not connect to PostgreSQL at … to verify version` | Auth or networking issue against the serverconf DB | Confirm `db.properties` credentials, that PostgreSQL is running, and the configured host/port is reachable |
| `Migration CLI step '<step>' reported an error: …` | The migration-CLI itself printed a stack trace | Inspect the log; fix the underlying issue (e.g. malformed `db.properties`); re-run the wizard — completed steps are skipped |
| `Service <name> did not stop within 60 seconds` | A service is stuck or has long-running connections | Investigate with `systemctl status <name>`; once stopped manually, re-run the wizard |
| `Package manager reported success but xroad-securityserver version did not change (still 7.8.x)` | V8 repository has no package for your CPU architecture, or the repo URL points to a wrong distribution | Verify `XROAD_REPO_BASE_URL` and `XROAD_REPO_MAIN`, confirm packages exist for your `dpkg --print-architecture` / `uname -m` |
| `configure-mirror-openbao-deb.sh not found at …` | The installer was not extracted with the bundled `lib/` helpers | Re-extract the installer tarball preserving directory structure |
| `signer-token-pins` skipped unexpectedly | `xroad-autologin` was not installed | Install `xroad-autologin` first or supply the script path explicitly |

**Getting help:**

If you cannot resolve a failure, attach the wizard log and the contents of `/var/lib/xroad-upgrade/` to your support request. `*` Confirm support contact for beta 2.

## References

1. [Security Server Installation Guide for Ubuntu / RHEL](ig-ss_x-road_v8_security_server_installation_guide.md)
2. [Security Server User Guide](ug-ss_x-road_6_security_server_user_guide.md)
3. [X-Road Terms and Abbreviations](../terms_x-road_docs.md)
4. `*` Migration-CLI release notes / changelog (URL TBD)
5. `*` X-Road 8 release notes (URL TBD)
