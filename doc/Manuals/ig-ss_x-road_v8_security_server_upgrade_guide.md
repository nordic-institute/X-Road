# Security Server Upgrade Guide: X-Road 7.8 to X-Road 8 <!-- omit in toc -->

**X-ROAD 8**

Version: 1.5
Doc. ID: IG-SS-8-UPGRADE

---

## Version history <!-- omit in toc -->

| Date       | Version | Description                                                                | Author             |
|------------|---------|----------------------------------------------------------------------------|--------------------|
| 19.05.2026 | 1.0     | Initial version                                                            | Egidijus Milierius |
| 06.07.2026 | 1.1     | Added missing information regarding auxiliary-service, reformatting tables | Marc David         |
| 19.10.2026 | 1.2     | Added the operational monitoring changes: settings migration, add-on flag, op-monitor package upgrade and TLS files | Eneli Reimets      |
| 17.11.2026 | 1.3     | Country package settings edits, combined package upgrade command, batch signing prompt for country profiles | Ričardas Bučiūnas  |
| 18.11.2026 | 1.4     | Added the DID and DSP port choice in the upgrade wizard                    | Justas Samuolis    |
| 23.11.2026 | 1.5     | Added the chapter [Upgrading External Operational Monitoring Daemon Host](#upgrading-external-operational-monitoring-daemon-host) | Eneli Reimets      |

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## Table of Contents <!-- omit in toc -->

- [License](#license)
- [Overview](#overview)
  - [What this guide covers](#what-this-guide-covers)
  - [What this guide does not cover](#what-this-guide-does-not-cover)
  - [Disclaimer](#disclaimer)
- [Prerequisites](#prerequisites)
- [Before you start](#before-you-start)
- [Upgrade with the wizard](#upgrade-with-the-wizard)
  - [Get the wizard](#get-the-wizard)
  - [Interactive mode](#interactive-mode)
  - [Unattended mode (env vars)](#unattended-mode-env-vars)
  - [Unattended mode (config file)](#unattended-mode-config-file)
  - [What the wizard does](#what-the-wizard-does)
  - [Migration-CLI sub-steps](#migration-cli-sub-steps)
  - [Edits in the country package settings file](#edits-in-the-country-package-settings-file)
  - [Operational monitoring](#operational-monitoring)
  - [Operator choices](#operator-choices)
  - [Secrets in unattended mode](#secrets-in-unattended-mode)
  - [Resuming after a failed run](#resuming-after-a-failed-run)
- [Manual upgrade procedure](#manual-upgrade-procedure)
- [Post-upgrade verification](#post-upgrade-verification)
- [Rollback](#rollback)
- [Upgrading External Operational Monitoring Daemon Host](#upgrading-external-operational-monitoring-daemon-host)
- [Troubleshooting](#troubleshooting)
- [References](#references)

## Overview

This guide walks you through upgrading an existing X-Road 7.8 Security Server to X-Road 8 (beta 2) using the official upgrade wizard (`xroad-upgrade.sh`). It also documents the equivalent manual procedure for situations where the wizard cannot be used, and provides rollback guidance.

### What this guide covers

- Single Security Server instances installed from native packages (DEB on Ubuntu, RPM on RHEL)
- Both interactive (whiptail) and unattended (env-var / config-file) execution modes
- Step-by-step manual procedure as an alternative to the wizard
- Post-upgrade verification
- Rollback options
- Upgrading an external operational monitoring daemon host, see [Upgrading External Operational Monitoring Daemon Host](#upgrading-external-operational-monitoring-daemon-host)

### What this guide does not cover

- **Central Server upgrades** — see [Central Server Upgrade Guide](ig-cs_x-road_v8_central_server_upgrade_guide.md)
- **Security Server cluster upgrades** — see chapter 7 "Upgrading a clustered X-Road Security Server installation" of the [External Load Balancer Installation Guide](LoadBalancing/ig-xlb_x-road_external_load_balancer_installation_guide.md). A clustered installation requires pausing replication between primary and secondaries, upgrading the primary first, then each secondary — running this single-host wizard on every node would conflict with the replicated `serverconf` database and the rsynced `/etc/xroad/signer/*`.
- **Container or Kubernetes deployments** — out of scope for beta 2
- Upgrade from X-Road versions older than 7.8.x — must first be brought to 7.8.x

### Disclaimer

This document applies to X-Road 8 Beta 2. Pre-release software may behave differently from the final X-Road 8.0 release.

## Prerequisites

Before starting, confirm the following on the Security Server:

| Requirement              | Notes                                                                                                                                                                                                                                          |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Operating system         | Ubuntu Server 22.04 / 24.04 LTS, or RHEL 9 / 10                                                                                                                                                                                                |
| Current X-Road version   | 7.8.x (any patch level). Earlier versions must first be upgraded to 7.8.x.                                                                                                                                                                     |
| PostgreSQL major version | 15 or newer. The wizard verifies this automatically.                                                                                                                                                                                           |
| Network reachability     | Outbound HTTPS to `https://artifactory.niis.org` (X-Road 8 packages, GPG key, migration CLI, bootstrap script) and `https://pkgs.openbao.org` (OpenBao packages).                                                                              |
| Root / sudo access       | Required for all steps                                                                                                                                                                                                                         |
| Backup                   | A current backup of the Security Server (configuration, databases, signer keys) is **strongly recommended** — see [Before you start](#before-you-start). The wizard also creates an automatic `/etc/xroad` snapshot before any on-disk change. |

Verify your current X-Road version:

```bash
# Ubuntu / Debian
dpkg-query -W -f='${Version}\n' xroad-proxy

# RHEL
rpm -q --queryformat '%{VERSION}\n' xroad-proxy
```

The output must begin with `7.8.`.

## Before you start

1. **Take a full backup.** The upgrade migrates configuration and signer state in place. A pre-upgrade backup is your safety net. Use the standard Security Server backup tooling (see the [Security Server User Guide](#references)) and copy the archive off the host. The wizard additionally writes a timestamped `xroad-pre-v8-backup-*.tar.gz` to `/etc/xroad/` before mutating any files, but that snapshot only covers `/etc/xroad` — your full backup also covers the databases and message log.

2. **Notify users and pause inbound traffic.** Services will be stopped for the duration of the upgrade.

3. **Take a host snapshot if possible** (LXD, hypervisor snapshot, cloud-provider snapshot). This is the cleanest rollback path — see [Rollback](#rollback).

4. **Decide on execution mode** — interactive for hands-on upgrades, unattended for Ansible or automated runs.

5. **Have these values ready** (you will pass them to the wizard):
   - `XROAD_MIGRATION_CLI_URL` — URL to `migration-cli.jar` for X-Road 8 beta 2 (`https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar`).
   - `XROAD_REPO_BASE_URL` — base URL of the X-Road 8 package repository (default: `https://artifactory.niis.org`).
   - `XROAD_REPO_MAIN` — repository name for your distribution. Defaults: `xroad8-snapshot-deb` on Ubuntu, `xroad8-snapshot-rpm` on RHEL.
   - `OPENBAO_MIRROR` and `OPENBAO_MIRROR_USER` — only if you use a private OpenBao mirror; leave empty for the official `pkgs.openbao.org`.

## Upgrade with the wizard

### Get the wizard

The recommended way to run the wizard on a host with internet access is the one-liner bootstrap:

```bash
sudo bash -c "$(curl -sSfL https://artifactory.niis.org/xroad-scripts/0.0.1-beta/upgrade-xroad.sh)" -- .
```

The bootstrap downloads `xroad-installer.tar.gz`, extracts it to a temporary directory, and invokes `xroad-upgrade.sh` from there. Trailing arguments after `--` are forwarded to `xroad-upgrade.sh`; the example above passes `.` so the wizard writes its log file (`xroad-upgrade-<timestamp>.log`) into the current working directory. Append `--config-file /path/to/xroad-upgrade.conf` to run unattended (see [Unattended mode (config file)](#unattended-mode-config-file)).

**Offline / air-gapped alternative.** Download the installer tarball on a host with network access and copy it to the target Security Server:

```bash
curl -fsSLO https://artifactory.niis.org/xroad-scripts/0.0.1-beta/xroad-installer.tar.gz
sudo tar -xzf xroad-installer.tar.gz -C /opt/
sudo bash /opt/xroad-installer/xroad-upgrade.sh
```

The wizard expects the bundled helper scripts under `lib/` and the migration task scripts under `tasks/migration/`, relative to the extracted directory.

### Interactive mode

Run the wizard as root. It prompts for confirmation at the version gate, before each migration CLI sub-step, for the soft token PIN, for the two operator choices (batch signing, strict identifier checks), and for the DID and DSP ports. The wizard does not ask about batch signing when the country profile already enables it, see [Operator choices](#operator-choices).

```bash
sudo bash /opt/xroad-installer/xroad-upgrade.sh
```

To pre-set values for non-interactive parameters, export them before invocation:

```bash
sudo XROAD_MIGRATION_CLI_URL='https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar' \
     XROAD_REPO_BASE_URL='https://artifactory.niis.org' \
     XROAD_REPO_MAIN='xroad8-snapshot-deb' \
     bash /opt/xroad-installer/xroad-upgrade.sh
```

### Unattended mode (env vars)

Set `XROAD_UPGRADE_UNATTENDED=true`. The wizard automatically sets `XROAD_UPGRADE_CONFIRMED=yes` and `XROAD_MIGRATION_UNATTENDED=true`, suppressing all whiptail dialogs.

```bash
sudo XROAD_UPGRADE_UNATTENDED=true \
     XROAD_MIGRATION_CLI_URL='https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar' \
     XROAD_REPO_BASE_URL='https://artifactory.niis.org' \
     XROAD_REPO_MAIN='xroad8-snapshot-deb' \
     XROAD_MIGRATION_SOFTTOKEN_PIN='<pin>' \
     bash /opt/xroad-installer/xroad-upgrade.sh
```

See [Secrets in unattended mode](#secrets-in-unattended-mode) for the PIN and keystore password handling.

### Unattended mode (config file)

Place a configuration file with the same variables and pass it via `--config-file`. Automation frameworks (Ansible, Salt, Puppet, etc.) should invoke the wizard with `XROAD_UPGRADE_UNATTENDED=true` and a `--config-file` — there is no separate automation path.

```bash
# /etc/xroad/xroad-upgrade.conf
XROAD_UPGRADE_UNATTENDED=true
XROAD_MIGRATION_CLI_URL=https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar
XROAD_REPO_BASE_URL=https://artifactory.niis.org
XROAD_REPO_MAIN=xroad8-snapshot-deb
XROAD_REPO_GPG_KEY_URL=
# Optional: override the full repo URL (overrides XROAD_REPO_BASE_URL + XROAD_REPO_MAIN)
XROAD_REPO_URL_OVERRIDE=
OPENBAO_MIRROR=
OPENBAO_MIRROR_USER=
XROAD_SS_PACKAGE=xroad-securityserver
# Delete obsolete V7 config files after a successful upgrade. Empty means "ask"
# (interactive) or "yes" (unattended). Set to "no" to keep them for review.
XROAD_DELETE_OBSOLETE_FILES=
# DID port and DSP port, 1024-65535, different from each other. A port that is not set is asked
# (interactive) or uses the default 7183 or 8183 (unattended). A port that is already stored is kept.
#XROAD_DID_PORT=7183
#XROAD_DSP_PORT=8183
```

```bash
sudo bash /opt/xroad-installer/xroad-upgrade.sh \
  --config-file /etc/xroad/xroad-upgrade.conf
```

Or via the bootstrap one-liner:

```bash
sudo bash -c "$(curl -sSfL https://artifactory.niis.org/xroad-scripts/0.0.1-beta/upgrade-xroad.sh)" -- \
  --config-file /etc/xroad/xroad-upgrade.conf
```

### What the wizard does

The wizard runs thirteen ordered steps and four sub-steps, 6a, 6b, 11a and 11b. Each step exits non-zero on failure; the wizard then halts and the services are left in whatever state the failing step produced (the per-step failure hint in the wizard log explains the precise state). The first seven steps and sub-steps 6a and 6b are all reversible without touching package state, so a preflight failure leaves the server unchanged.

| #   | Step                                  | What it does                                                                                                                                                                                                                                                                                                          |
|-----|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1   | **Version gate**                      | Reads installed `xroad-proxy` version; aborts unless it matches `^7\.8\.`. In interactive mode it asks for confirmation; in unattended mode it relies on `XROAD_UPGRADE_CONFIRMED=yes`.                                                                                                                               |
| 2   | **Download migration CLI**            | Fetches `migration-cli.jar` (and `migration-cli.jar.sha256`) from `XROAD_MIGRATION_CLI_URL`, verifies the SHA-256, writes the JAR to `/var/tmp/migration-cli.jar` (mode 600).                                                                                                                                         |
| 3   | **Back up /etc/xroad**                | Snapshots `/etc/xroad` to `/etc/xroad/xroad-pre-v8-backup-<timestamp>.tar.gz` (mode 600). On a resumed run an existing snapshot is preserved.                                                                                                                                                                         |
| 4   | **Migrate db.properties**             | Rewrites `/etc/xroad/db.properties` in place to the V8 `xroad.db.*` key prefix. Original is preserved at `/etc/xroad/db.properties.bak`.                                                                                                                                                                              |
| 5   | **PostgreSQL pre-flight**             | Parses the migrated `db.properties`, connects to the `serverconf` database, verifies PostgreSQL major version ≥ 15.                                                                                                                                                                                                   |
| 6   | **OpenBao repository setup**          | Adds the OpenBao apt/yum repository so the 8.0 packages can pull in OpenBao dependencies.                                                                                                                                                                                                                             |
| 6a  | **Record op-monitor state**           | Before the services stop, records once in `/var/lib/xroad-upgrade/op-monitor.state` whether the X-Road 7.8 operational monitoring add-on is enabled (its add-on configuration file is present) and whether the `xroad-opmonitor` package is installed. A resumed run reuses the stored state.                         |
| 6b  | **Record country package**            | Before the services stop, records once in `/var/lib/xroad-upgrade/country-package.state` which country package (`xroad-securityserver-<cc>`) is installed, or `none`. A resumed run reuses the stored state. The step stops when more than one country package is installed.                                                                                     |
| 7   | **Stop X-Road services**              | Discovers all active `xroad-*` units via `systemctl` and stops each one, waiting up to 60 s per service to reach inactive state.                                                                                                                                                                                      |
| 8   | **Switch to V8 repository**           | Backs up the existing X-Road sources file(s) with a `.v7.bak.<timestamp>` suffix, writes the V8 sources file, imports the V8 GPG key, refreshes package metadata.                                                                                                                                                     |
| 9   | **Upgrade packages**                  | Runs `apt-get install -y` / `dnf install -y` for the package named by `XROAD_SS_PACKAGE` (default `xroad-securityserver`). When `xroad-opmonitor` was installed in 7.8, it is installed in the same command and the step fails unless it is at 8.x afterwards, so the proxy and op-monitor are upgraded together. The country package recorded in step 6b is handled the same way, so the server keeps its country profile.     |
| 10  | **Migrate TLS to secret store**       | Reads `/etc/xroad/ssl/*.crt` and matching keys for `internal`, `proxy-ui-api`, `center-admin-service` and `management-service`, then writes them to OpenBao under `xrd-secret/tls/{internal,admin-service,management-service}`. Missing pairs are skipped. `opmonitor.crt` and its key are not migrated: a local op-monitor uses the shared internal gRPC identity. |
| 11  | **Run migration CLI**                 | Runs the migration-CLI sub-steps (see [Migration-CLI sub-steps](#migration-cli-sub-steps)) with sentinel-based idempotency under `/var/lib/xroad-upgrade/`.                                                                                                                                                           |
| 11a | **Write op-monitor flag**             | Writes `xroad.proxy.addon.op-monitor.enabled` as `true` or `false` from the state recorded in step 6a, only when the property has no value yet. See [Operational monitoring](#operational-monitoring).                                                                                                                |
| 11b | **Store DID and DSP ports**          | Runs `write_dsp_ports.sh`: writes the ports chosen after the version gate (see [Operator choices](#operator-choices)) as `xroad.dsp.did-port` and `xroad.dsp.dsp-port`, before the services start. A port equal to its default is not written, an existing row is kept, and a secondary node is skipped. Step 9 keeps `xroad-ds-identity-hub` and `xroad-ds-control-plane` from starting until step 12.                                                                                  |
| 12  | **Start X-Road services**             | Starts `xroad-signer`, `xroad-ds-identity-hub`, `xroad-ds-control-plane`, `xroad-proxy`, `xroad-opmonitor`, `xroad-monitor`, `xroad-proxy-ui-api`, `xroad-auxiliary-service` in that order, waiting for each to reach active state. Services whose unit files are not installed (for example an absent op-monitor on a minimal installation) are skipped with a warning. |
| 13  | **Clean up obsolete V7 config files** | Removes V7 configuration files no longer read by V8 (see [Manual upgrade procedure](#manual-upgrade-procedure) for the list). Interactive mode prompts; unattended deletes by default unless `XROAD_DELETE_OBSOLETE_FILES=no`.                                                                                        |

On success the wizard prints `X-Road 8.0 upgrade completed successfully!` and the path of its log file.

### Migration-CLI sub-steps

Step 11 invokes `migration-cli.jar` for the sub-commands listed below. Each sub-step is gated by a sentinel file under `/var/lib/xroad-upgrade/step-<id>.done`, so re-running the wizard after a fix skips already-completed sub-steps automatically. Sub-steps whose source file is absent are skipped with an info log.

| Sub-step                                  | What it migrates                                                                                                                                                                                                                             |
|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `validate`                                | Read-only sanity check: prerequisites and configuration database connectivity. Nothing is written.                                                                                                                                           |
| `configuration-anchor`                    | The configuration anchor XML (path from `local.ini` `proxy.configuration-anchor-file`, default `/etc/xroad/configuration-anchor.xml`) into the configuration database.                                                                       |
| `signer-devices`                          | HSM / signer module declarations from `devices.ini` (path from `local.ini` `signer.device-configuration-file`, default `/etc/xroad/devices.ini`) into the configuration database under the signer scope.                                     |
| `ini-to-db` (per file)                    | Each `override-*.ini` then `local.ini` from `/etc/xroad/conf.d/` into the configuration database. `override-securityserver-*.ini` (the X-Road 7 country package settings) is skipped: X-Road 8 ships the country values inside the product. Edits made in this file are not migrated, see [Edits in the country package settings file](#edits-in-the-country-package-settings-file).                                                                                                                                            |
| `properties-to-db` (ssl)                  | The SSL properties file (path from `local.ini` `proxy-ui-api.ssl-properties`, default `/etc/xroad/ssl.properties`) under the proxy-ui-api scope.                                                                                             |
| `keyconf`                                 | The signer keyconf (keys, certificates, soft token credentials) from `/etc/xroad/signer` into the configuration database. Prompts for the soft token PIN; in unattended mode `XROAD_MIGRATION_SOFTTOKEN_PIN` must be set.                    |
| `signer-token-pins`                       | Soft token PINs from `xroad-autologin` fetch-pin scripts into the OpenBao secret store. Skipped if `xroad-autologin` is not installed.                                                                                                       |
| `file-to-db` (acme)                       | The contents of `/etc/xroad/conf.d/acme.yml` into the configuration database under key `xroad.acme` (proxy-ui-api scope), with the `account-keystore-password` line stripped first.                                                         |
| `acme-account-keys`                       | Every alias in the X-Road 7 ACME account keystore (`acme.p12`) into the OpenBao secret store: the key pair plus the alias's existing certificate expiry, carried forward as the rotation-due timestamp. The certificate itself is discarded. PKCS12 lowercases aliases at write time, so the original case is recovered by matching client identifiers configured in the configuration database; an alias with no matching client is skipped (logged), the rest of the batch still migrates. |
| `file-to-db` (mail)                       | The contents of `/etc/xroad/conf.d/mail.yml` into the configuration database under key `xroad.mail-notification` (proxy-ui-api scope).                                                                                                       |
| `pgp-keys`                                | Message-log archive PGP keys from the GPG home directory (per `message-log.archive-gpg-home-directory` in `local.ini`, default `/etc/xroad/gpghome`) into the OpenBao secret store.                                                          |
| `messagelog-key-mappings`                 | The message-log archive encryption key mapping file (path from `local.ini` `message-log.archive-encryption-keys-config`; no default) into the configuration database.                                                                        |
| `messagelog-db-encryption-keys`           | The X-Road 7 message-log database encryption key from a PKCS#12 keystore (settings from `local.ini` `[message-log]`: `messagelog-keystore`, `messagelog-keystore-password`, `messagelog-key-id`) into the OpenBao secret store.              |
| `set-property` (batch signing)            | Sets `xroad.proxy.batch-signing-enabled=true` only if the operator chose to preserve the X-Road 7 behavior. The wizard does not ask about batch signing when the country profile already enables it. See [Operator choices](#operator-choices).                                                                                       |
| `set-property` (strict identifier checks) | Sets `xroad.proxy.strict-identifier-checks=false` if the operator chose to preserve the X-Road 7 behavior. See [Operator choices](#operator-choices).                                                                                        |

`account-keystore-password` is not carried into the `xroad.acme` database value: X-Road 8 has no code path that reads it back out, so the wizard writes a scrubbed copy of `acme.yml` to the database and leaves the file on disk unchanged. If you inspect `xroad.acme` after migration and expect to see that field, this is why it's missing.

Inspect the wizard log for any Java stack traces. Migration-CLI sub-commands sometimes return exit 0 even after an internal exception; the wizard scans the output and treats any line matching `Error `, `Caused by:`, `Exception in `, or `<Class>Exception:` as a failure.

### Edits in the country package settings file

An X-Road 7.8 country package keeps its settings in `/etc/xroad/conf.d/override-securityserver-<cc>.ini`. X-Road 8 takes these values from the country profile in the product. The wizard installs the country package that step 6b recorded, so the server keeps its country profile.

The wizard does not migrate edits that you made in `override-securityserver-*.ini`. The wizard skips this file and writes an information message to the log. Step 13 can remove the file, and the `/etc/xroad` snapshot from step 3 keeps a copy.

Enter each edited value again as a system parameter, in the admin UI (**Settings > System Parameters**) or from the command line:

```bash
/usr/share/xroad/scripts/db_property.sh set <system parameter name> <value>
```

A system parameter that you set takes precedence over the country profile. The **Source** column of the admin UI then shows `Database` for it. For the installation of a country package and the order of precedence, see section 2.6.2 of the [Security Server Installation Guide](ig-ss_x-road_v8_security_server_installation_guide.md#262-country-profile).

### Operational monitoring

X-Road 8 stores operational monitoring data and answers monitoring queries over gRPC only. The REST interface of the operational monitoring daemon and its port 2080 are gone, so the proxy and the operational monitoring daemon must be upgraded together: an X-Road 8 proxy cannot use an X-Road 7.8 daemon. The wizard upgrades the local `xroad-opmonitor` package in step 9 when it was installed in 7.8. A daemon on another host is not upgraded by the wizard. To upgrade it, see [Upgrading External Operational Monitoring Daemon Host](#upgrading-external-operational-monitoring-daemon-host).

A query to a stopped or unreachable operational monitoring daemon fails only after the channel deadline (`xroad.common-rpc.channel.op-monitor.deadline-after`, 60 seconds by default), not immediately.

The `ini-to-db` sub-step migrates the X-Road 7.8 `[op-monitor]`, `[op-monitor-service]` and `[op-monitor-buffer]` settings as follows.

| X-Road 7.8 setting                                                                                                                                                   | Result                                                                                                           |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `[op-monitor]` `grpc-port`                                                                                                                                           | Mapped to `xroad.op-monitor.rpc.port` and `xroad.common-rpc.channel.op-monitor.port`.                            |
| `[op-monitor]` `grpc-client-timeout`                                                                                                                                 | Mapped to `xroad.common-rpc.channel.op-monitor.deadline-after`.                                                  |
| `[op-monitor-buffer]` `size`, `max-records-in-message`, `sending-interval-seconds`                                                                                   | Mapped to `xroad.proxy.addon.op-monitor.buffer.size`, `.max-records-in-message` and `.sending-interval-seconds`. |
| `[op-monitor]` `keep-records-for-days`, `clean-interval`, `max-records-in-payload`, `records-available-timestamp-offset-seconds`, `health-statistics-period-seconds` | Kept under the same names with the prefix `xroad.op-monitor.`.                                                   |
| `[op-monitor]` `host`                                                                                                                                                | Mapped by its value, see below.                                                                                  |
| `[op-monitor]` `port`, `scheme`, `tls-certificate`, `client-tls-certificate`                                                                                         | Dropped. X-Road 8 has no REST connection.                                                                        |
| `[op-monitor-service]` and `[op-monitor-buffer]` `socket-timeout-seconds`, `connection-timeout-seconds`                                                              | Dropped. The channel deadline `deadline-after` replaces the HTTP timeouts.                                       |

`max-records-in-payload` keeps its X-Road 7.8 value. X-Road 8 also limits the size of a query response with `xroad.common-rpc.channel.op-monitor.max-inbound-message-size` (16 MiB by default); a larger response fails. The response size depends on the number and the size of the records, and a full response also includes all records of its last timestamp second, so neither the default nor a migrated value of `max-records-in-payload` guarantees that every response fits. A monitoring client that repeats a failed query from the same position does not progress until the response fits. The available settings and their limits are described in the [operational monitoring system parameters](../OperationalMonitoring/Manuals/ug-opmonsyspar_x-road_operational_monitoring_system_parameters_Y-1099-1.md).

If the effective `host` value is a remote host, the daemon settings `xroad.op-monitor.rpc.port`, `xroad.op-monitor.keep-records-for-days`, `xroad.op-monitor.clean-interval`, `xroad.op-monitor.max-records-in-payload`, `xroad.op-monitor.records-available-timestamp-offset-seconds` and `xroad.op-monitor.health-statistics-period-seconds` are not migrated. Set them in `op-monitor-settings.yaml` on the external host.

The effective 7.8 `host` value is the last one set in the `override-*.ini` files in name order, then `local.ini`. A value set only in the package file `op-monitor.ini` is not migrated. An IPv6 address in square brackets, such as `[::1]`, is migrated without the brackets.

| `host` value                                                                                                     | Result                                                                                                                                                                                                    |
|------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Absent                                                                                                           | Nothing is written. The X-Road 8 defaults apply: channel host and listener address `127.0.0.1`.                                                                                                           |
| A wildcard address such as `0.0.0.0` or `::`                                                                     | `xroad.op-monitor.rpc.listen-address` is set to the value. The channel host keeps its default.                                                                                                            |
| `127.0.0.1`                                                                                                      | `xroad.common-rpc.channel.op-monitor.host` is set to the value.                                                                                                                                           |
| Another loopback IP address such as `::1`, or an IP address of this server                                       | The channel host and `xroad.op-monitor.rpc.listen-address` are set to the value. The IP SAN setting `xroad.common-rpc.certificate-provisioning.ip-subject-alt-names` is set to `127.0.0.1` and the value. |
| A remote address                                                                                                 | Only the channel host is set. The listener address keeps its default and no certificate SAN setting is written.                                                                                           |
| A name with at least one address of this server that is not a loopback address                                   | The channel host and `xroad.op-monitor.rpc.listen-address` are set to the value, and the DNS SAN setting `xroad.common-rpc.certificate-provisioning.alt-names` is set to the value.                       |
| A name whose addresses are all loopback addresses, one of them `127.0.0.1`, such as `localhost`                  | The channel host is set to the value and the DNS SAN setting is set to the value. The listener address keeps its default.                                                                                 |
| A name whose addresses are all loopback addresses other than `127.0.0.1`, such as only `127.0.1.1` or only `::1` | The channel host and `xroad.op-monitor.rpc.listen-address` are set to the value, and the DNS SAN setting is set to the value.                                                                             |
| A name that resolves to this server and to a remote address                                                      | Ambiguous. Only the channel host is set and a warning is logged. The listener address keeps its default and no certificate SAN setting is written.                                                      |
| A name that resolves to a loopback address and a remote address                                                  | Ambiguous. Only the channel host is set and a warning is logged. The listener address keeps its default and no certificate SAN setting is written.                                                      |
| A name that resolves only to remote addresses                                                                    | Only the channel host is set. The listener address keeps its default and no certificate SAN setting is written.                                                                                           |
| A name that cannot be resolved                                                                                   | Ambiguous. Only the channel host is set and a warning is logged. The listener address keeps its default and no certificate SAN setting is written.                                                      |

For an ambiguous `host` value the wizard log file has a line of the form `[<timestamp>] [WARN] op-monitor host '<name>' …` and gives the reason: the name cannot be resolved, it resolves to this server and to another host, or it resolves to a loopback address and to another host. The channel host keeps the name, and no listener address or certificate name is written. If the operational monitoring daemon runs on this Security Server, set the channel host to the loopback address and restart the proxy and the admin-service:

```bash
/usr/share/xroad/scripts/db_property.sh set xroad.common-rpc.channel.op-monitor.host 127.0.0.1 --yes
service xroad-proxy restart
service xroad-proxy-ui-api restart
```

If the daemon runs on an external host, no change is needed.

The certificate SAN settings are configuration rows. The migration writes only these rows; it does not copy or modify certificate files. The internal gRPC services of the Security Server share these provisioning settings: each service requests its own certificate with them from the local secret store when it starts and when the certificate is refreshed. The IP SAN setting always contains `127.0.0.1`, which the internal services connect to by default. The migration does not change the common name of the certificates.

Operational monitoring on and off:

- In X-Road 7.8 the add-on package `xroad-addon-opmonitoring` turns the monitoring on. In X-Road 8 the property `xroad.proxy.addon.op-monitor.enabled` (default `false`; the EE, FI, IS and FO country profiles set it to `true`) does. Step 6a records the 7.8 state and step 11a writes the property: `true` when the add-on was enabled, otherwise `false`. A value that already exists is kept.
- Resetting `/var/lib/xroad-upgrade` removes the recorded state. A rerun then records the state again from the already upgraded server, where the 7.8 add-on is gone, so monitoring that was enabled in 7.8 is recorded as disabled. If the property was not written yet, set it by hand as in the [manual procedure](#manual-upgrade-procedure), step 11a.

The op-monitor TLS files are not migrated. X-Road 7.8 used `/etc/xroad/ssl/opmonitor.crt` and its key for the HTTPS connection to the daemon. A daemon on the Security Server host uses the shared internal gRPC identity of the Security Server, so step 10 does not copy them to the secret store. An external daemon uses its own file identity, see [Upgrading External Operational Monitoring Daemon Host](#upgrading-external-operational-monitoring-daemon-host).

### Operator choices

Two semantics changed between X-Road 7 and X-Road 8. The wizard asks once for each (interactive) or applies a documented default (unattended). The wizard also asks for the DID and DSP ports, described last.

**Batch signing** (`xroad.proxy.batch-signing-enabled`).

- X-Road 7 behavior: enabled. The signer batches multiple message signatures into a single signing operation.
- X-Road 8 default: disabled. Each message is signed separately.
- Interactive prompt default: disabled (X-Road 8 default).
- Unattended default: disabled (X-Road 8 default).
- Choose "Yes" only if you intentionally rely on the X-Road 7 batching behavior.
- The wizard does not ask about batch signing when the country profile already enables it. It writes a log message and records the step as done. An unattended run needs no setting. To disable batch signing afterwards, set `xroad.proxy.batch-signing-enabled` to `false` as a system parameter.

**Strict identifier checks** (`xroad.proxy.strict-identifier-checks`).

- What it validates: X-Road identifier fields (instance, member class, member code, subsystem, etc.). The allowed character set consists of `A–Z`, `a–z`, `0–9`, and the following characters: '()+,-.=?. Any other character (including spaces, slashes, brackets, or accented characters) makes the identifier invalid.
- X-Road 7 behavior: disabled. Invalid characters produce a warning in the proxy log; the request continues.
- X-Road 8 default: enabled. Invalid characters cause the request to fail with `INVALID_CLIENT_IDENTIFIER`.
- Interactive prompt default: keep disabled (preserve X-Road 7 behavior). Choose "No" to opt into the stricter X-Road 8 default.
- Unattended default: keep disabled (preserve X-Road 7 behavior), so an automated upgrade does not silently break clients that were already sending identifiers that the V8 stricter rules would reject. Audit your traffic and flip the property to `true` once you have verified that no client identifiers contain disallowed characters.

**DID and DSP ports** (`xroad.dsp.did-port`, `xroad.dsp.dsp-port`).

- What they set: the port on which the server serves the documents of its decentralized identifiers (DIDs), and the port for the Dataspace Protocol (DSP).
- Defaults: 7183 for the DID port and 8183 for the DSP port. Each port is a number from 1024 to 65535, and the two ports must be different.
- Interactive prompt: the wizard asks for each port right after the version gate, before it changes anything, with the default filled in. An invalid answer is asked again, and cancelling the question keeps the default.
- Pre-set values: `XROAD_DID_PORT` and `XROAD_DSP_PORT`, set in the `--config-file` (`xroad-upgrade.conf`, see `xroad-upgrade.conf.sample`). A port that is set is not asked. An invalid value stops the wizard before it changes anything.
- Unattended default: with `XROAD_UPGRADE_UNATTENDED=true` the wizard does not ask, and a port that is not set uses its default.
- Storage: step 11b writes the chosen ports to the configuration database before the services start. A port equal to its default is not written, because a missing row means the default. A port that already has a row keeps the existing value.
- Secondary node: the wizard does not ask and writes nothing when `/etc/xroad/conf.d/node.ini` has the value `secondary` (the legacy value `slave` also counts) for `type` in the `[node]` section, or when `XROAD_IGNORE_DATABASE_SETUP` is set. The primary node owns the rows. See [2.6.3 DID and DSP ports](ig-ss_x-road_v8_security_server_installation_guide.md#263-did-and-dsp-ports) in the Security Server Installation Guide.
- Choose the ports now. A port cannot change after its DID is bound, and the upgraded server is already registered, so its DIDs are bound soon after X-Road 8 starts.

### Secrets in unattended mode

Two migration sub-steps require credentials. The wizard does not prescribe how to supply them; choose whichever method fits your secret-management practice.

| Variable                                       | Required when                                                                   | Used by                                  |
|------------------------------------------------|---------------------------------------------------------------------------------|------------------------------------------|
| `XROAD_MIGRATION_SOFTTOKEN_PIN`                | A soft token keystore exists at `/etc/xroad/signer/softtoken/.softtoken.p12`    | `keyconf` sub-step                       |
| `XROAD_MIGRATION_MESSAGELOG_KEYSTORE_PASSWORD` | `message-log.messagelog-keystore` in `local.ini` points to an existing keystore | `messagelog-db-encryption-keys` sub-step |
| `XROAD_MIGRATION_ACME_KEYSTORE_PASSWORD`       | `/etc/xroad/ssl/acme.p12` exists (value comes from the old `acme.yml`)          | `acme-account-keys` sub-step             |

If your X-Road 7 keystore password was supplied via the `ACCOUNT_KEYSTORE_PASSWORD` environment variable rather than `acme.yml`'s `account-keystore-password` field, export `ACCOUNT_KEYSTORE_PASSWORD` in the shell running the wizard — the wizard falls back to it when `acme.yml` doesn't have the field, matching X-Road 7's own lookup order.

Common delivery options:

- Place the values in a sibling env file with `chmod 600` and `source` it from your Ansible / shell wrapper just before invoking the wizard.
- Add them to the `--config-file` (also `chmod 600`).
- Pass them on the invocation line itself.

The wizard unsets both variables immediately after the sub-step that consumes them.

### Resuming after a failed run

Sentinel files under `/var/lib/xroad-upgrade/` record which migration-CLI sub-steps already succeeded. After fixing the underlying issue, re-run the wizard with the same arguments — completed sub-steps are skipped automatically.

To reset and rerun every sub-step (for example after a snapshot restore):

```bash
sudo rm -rf /var/lib/xroad-upgrade
```

The reset also removes the operational monitoring state recorded in step 6a and the country package recorded in step 6b. Reset only before step 9 has upgraded the packages, or after a snapshot restore. See [Operational monitoring](#operational-monitoring).

## Manual upgrade procedure

Use this procedure if the wizard is unavailable, or if you need fine-grained control. The steps mirror what the wizard does internally. Run all commands as root.

**1. Verify version.**

```bash
# Ubuntu
dpkg-query -W -f='${Version}\n' xroad-proxy
# RHEL
rpm -q --queryformat '%{VERSION}\n' xroad-proxy
```

Confirm the output begins with `7.8.`. Stop here if it does not.

**2. Download the migration-CLI artifact and verify checksum.**

```bash
curl -fsSL https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar       -o /var/tmp/migration-cli.jar
curl -fsSL https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar.sha256 -o /var/tmp/migration-cli.jar.sha256
( cd /var/tmp && awk '{print $1"  migration-cli.jar"}' migration-cli.jar.sha256 | sha256sum -c - )
chmod 600 /var/tmp/migration-cli.jar
```

**3. Snapshot /etc/xroad.**

```bash
TS=$(date +%Y-%m-%d_%H%M%S)
tar -czf "/var/tmp/xroad-pre-v8-backup-${TS}.tar.gz" -C /etc xroad
chmod 600 "/var/tmp/xroad-pre-v8-backup-${TS}.tar.gz"
mv "/var/tmp/xroad-pre-v8-backup-${TS}.tar.gz" /etc/xroad/
```

**4. Migrate db.properties to V8 format.** Prefix every `key = value` line with `xroad.db.` (comments, blank lines, and already-prefixed lines pass through). Keep a `.bak` of the original.

```bash
cp -p /etc/xroad/db.properties /etc/xroad/db.properties.bak
sed -E -i '/^[[:space:]]*([#!]|xroad\.db\.|$)/b; s/^([[:space:]]*)([^=[:space:]]+)([[:space:]]*=)/\1xroad.db.\2\3/' \
  /etc/xroad/db.properties
```

**5. Verify PostgreSQL version.** Read the serverconf JDBC URL from `/etc/xroad/db.properties` and connect:

```bash
PGPASSWORD='<serverconf-password>' \
  psql -w -h <host> -p <port> -U <serverconf-user> -d postgres \
  -tAc 'SHOW server_version_num'
```

The numeric server version divided by 10000 must be ≥ 15.

**6. Configure the OpenBao package repository.**

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

**6a. Record the operational monitoring state.** Before the services are stopped, note whether the X-Road 7.8 operational monitoring add-on is enabled and whether the daemon package is installed:

```bash
test -f /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf && echo "add-on enabled" || echo "add-on not enabled"
# Ubuntu
dpkg-query -W -f='${Status}\n' xroad-opmonitor
# RHEL
rpm -q xroad-opmonitor
```

**6b. Record the country package.** Before the services are stopped, note which country package, if any, is installed:

```bash
# Ubuntu
dpkg-query -W -f='${Package} ${Status}\n' 'xroad-securityserver-*' | grep 'install ok installed'
# RHEL
rpm -qa 'xroad-securityserver-*'
```

**7. Stop all X-Road services.**

```bash
mapfile -t SERVICES < <(systemctl list-units --type=service --state=active 'xroad-*' \
  --no-legend --plain | awk '{print $1}')
for s in "${SERVICES[@]}"; do
  systemctl stop "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" || break; sleep 2; done
done
```

**8. Switch the X-Road package repository from V7 to V8.**

```bash
# Ubuntu
TS=$(date +%Y%m%d-%H%M%S)
mv /etc/apt/sources.list.d/xroad.list /etc/apt/sources.list.d/xroad.list.v7.bak."$TS"
curl -fsSL https://artifactory.niis.org/api/gpg/key/public \
  -o /usr/share/keyrings/xroad-keyring.asc
CODENAME=$(lsb_release -sc)
echo "deb [signed-by=/usr/share/keyrings/xroad-keyring.asc] https://artifactory.niis.org/xroad8-snapshot-deb ${CODENAME}-current main" \
  > /etc/apt/sources.list.d/xroad.list
apt-get update

# RHEL
TS=$(date +%Y%m%d-%H%M%S)
for repo in /etc/yum.repos.d/xroad*.repo; do
  [ -f "$repo" ] && mv "$repo" "${repo}.v7.bak.${TS}"
done
yum-config-manager --add-repo "https://artifactory.niis.org/xroad8-snapshot-rpm"
rpm --import https://artifactory.niis.org/api/gpg/key/public
yum makecache
```

**8a. (Ubuntu) Keep the dataspace services stopped during the upgrade.** The Ubuntu package scripts start `xroad-ds-identity-hub` and `xroad-ds-control-plane` and restart `xroad-proxy-ui-api` during step 9, before step 11b can write the DID and DSP ports. Mask the three units so that they stay stopped until step 12. Masking works before `xroad-ds-identity-hub` and `xroad-ds-control-plane` exist on the X-Road 7 host. On RHEL the package scripts do not start services, so skip this step.

```bash
# Ubuntu
systemctl mask xroad-ds-identity-hub.service xroad-ds-control-plane.service xroad-proxy-ui-api.service
```

**9. Upgrade the Security Server package.** If the `xroad-opmonitor` package is installed, upgrade it in the same command so that the proxy and the operational monitoring daemon are upgraded together. Do the same with the country package found in step 6b (shown below as `xroad-securityserver-<cc>`).

```bash
# Ubuntu
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-securityserver
# Ubuntu, when xroad-opmonitor is installed
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-securityserver xroad-opmonitor
# RHEL
dnf install -y xroad-securityserver
# RHEL, when xroad-opmonitor is installed
dnf install -y xroad-securityserver xroad-opmonitor
# Ubuntu, when a country package is installed
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-securityserver xroad-securityserver-<cc>
# RHEL, when a country package is installed
dnf install -y xroad-securityserver xroad-securityserver-<cc>
# Ubuntu, when xroad-opmonitor and a country package are installed
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-securityserver xroad-opmonitor xroad-securityserver-<cc>
# RHEL, when xroad-opmonitor and a country package are installed
dnf install -y xroad-securityserver xroad-opmonitor xroad-securityserver-<cc>
```

Verify the version actually changed:

```bash
# Ubuntu
dpkg-query -W -f='${Version}\n' xroad-securityserver
# RHEL
rpm -q --queryformat '%{VERSION}\n' xroad-securityserver
```

The output must now begin with `8.`.

**10. Migrate TLS material to the local secret store.** For each existing pair `/etc/xroad/ssl/{internal,proxy-ui-api,center-admin-service,management-service}.{crt,key}` write a JSON `{"certificate": "...", "privateKey": "..."}` payload to `POST <secret-store>/v1/xrd-secret/tls/<path>`, where `<path>` is `internal`, `admin-service` (used for both `proxy-ui-api` and `center-admin-service`), or `management-service`. Do not migrate `opmonitor.crt` and its key. Secret-store address and root token come from `/etc/xroad/services/secret-store-local.conf` (`XROAD_SECRET_STORE_{SCHEME,HOST,PORT,TOKEN}`).

**11. Run migration-CLI sub-steps.** Run each sub-step listed in [Migration-CLI sub-steps](#migration-cli-sub-steps) in order, only when the corresponding source file is present:

```bash
java -jar /var/tmp/migration-cli.jar validate
java -jar /var/tmp/migration-cli.jar configuration-anchor /etc/xroad/configuration-anchor.xml /etc/xroad/db.properties
java -jar /var/tmp/migration-cli.jar signer-devices       /etc/xroad/devices.ini             /etc/xroad/db.properties
for ini in /etc/xroad/conf.d/override-*.ini /etc/xroad/conf.d/local.ini; do
  [ -f "$ini" ] && java -jar /var/tmp/migration-cli.jar ini-to-db "$ini" /etc/xroad/db.properties
done
java -jar /var/tmp/migration-cli.jar properties-to-db /etc/xroad/ssl.properties /etc/xroad/db.properties proxy-ui-api
XROAD_MIGRATION_SOFTTOKEN_PIN='<pin>' \
  java -jar /var/tmp/migration-cli.jar keyconf /etc/xroad/signer /etc/xroad/db.properties

# Optional: only if xroad-autologin is installed
java -jar /var/tmp/migration-cli.jar signer-token-pins

java -jar /var/tmp/migration-cli.jar file-to-db /etc/xroad/conf.d/acme.yml /etc/xroad/db.properties xroad.acme              proxy-ui-api
java -jar /var/tmp/migration-cli.jar file-to-db /etc/xroad/conf.d/mail.yml /etc/xroad/db.properties xroad.mail-notification proxy-ui-api

# Only if an ACME account keystore exists
XROAD_MIGRATION_ACME_KEYSTORE_PASSWORD='<pw>' \
  java -jar /var/tmp/migration-cli.jar acme-account-keys /etc/xroad/ssl/acme.p12 /etc/xroad/db.properties

java -jar /var/tmp/migration-cli.jar pgp-keys                /etc/xroad/conf.d/local.ini
java -jar /var/tmp/migration-cli.jar messagelog-key-mappings <mapping-file>  /etc/xroad/db.properties
XROAD_MIGRATION_MESSAGELOG_KEYSTORE_PASSWORD='<pw>' \
  java -jar /var/tmp/migration-cli.jar messagelog-db-encryption-keys <keystore-path> <key-id>
```

Inspect the output of each command. **Treat any Java stack trace as a failure even if the process exits 0** — do not proceed if a step printed an exception.

Then apply the operator choices documented in [Operator choices](#operator-choices) via `set-property`, for example to preserve the X-Road 7 strict-identifier-checks behavior:

```bash
java -jar /var/tmp/migration-cli.jar set-property /etc/xroad/db.properties xroad.proxy.strict-identifier-checks false
```

**11a. Write the operational monitoring flag.** Set `xroad.proxy.addon.op-monitor.enabled` to `true` if the add-on was enabled in step 6a, otherwise to `false`. `--if-absent` keeps an existing value.

```bash
/usr/share/xroad/scripts/db_property.sh set xroad.proxy.addon.op-monitor.enabled true --if-absent
```

**11b. Write the DID and DSP ports.** Skip this step on a secondary node. Set the ports chosen for this server. A port equal to its default (7183 or 8183) needs no row, and `--if-absent` keeps an existing value. Each port is a number from 1024 to 65535, and the two ports must be different. The example uses 7190 and 8190:

```bash
/usr/share/xroad/scripts/db_property.sh set xroad.dsp.did-port 7190 --if-absent
/usr/share/xroad/scripts/db_property.sh set xroad.dsp.dsp-port 8190 --if-absent
```

Write the ports before `xroad-ds-identity-hub`, `xroad-ds-control-plane` and `xroad-proxy-ui-api` start, which is in step 12 because step 8a keeps them masked on Ubuntu: a port cannot change after its DID is bound.

**12. Start X-Road services.** On Ubuntu, first unmask the units masked in step 8a.

```bash
# Ubuntu
systemctl unmask xroad-ds-identity-hub.service xroad-ds-control-plane.service xroad-proxy-ui-api.service
systemctl daemon-reload
systemctl preset xroad-ds-identity-hub.service xroad-ds-control-plane.service xroad-proxy-ui-api.service
```

Then start the services:

```bash
for s in xroad-signer xroad-ds-identity-hub xroad-ds-control-plane xroad-proxy xroad-opmonitor xroad-monitor xroad-proxy-ui-api xroad-auxiliary-service; do
  systemctl cat "$s" >/dev/null 2>&1 || { echo "skip $s (not installed)"; continue; }
  systemctl start "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" && break; sleep 2; done
done
```

**13. Clean up obsolete V7 config files.** Once you have verified the upgrade (see [Post-upgrade verification](#post-upgrade-verification)) the following V7 paths can be deleted — they are no longer read by X-Road 8:

```
/etc/xroad/conf.d/*.ini
/etc/xroad/devices.ini
/etc/xroad/configuration-anchor.xml
/etc/xroad/signer            (directory)
/etc/xroad/conf.d/*-logback*.xml
/etc/xroad/conf.d/acme.yml
/etc/xroad/conf.d/mail.yml
/etc/xroad/db.properties.bak
```

## Post-upgrade verification

**1. Check all expected services are active:**

```bash
systemctl list-units --type=service --state=active 'xroad-*'
```

You should see `xroad-signer`, `xroad-proxy`, `xroad-monitor`, `xroad-proxy-ui-api`, `xroad-auxiliary-service`, and `xroad-opmonitor` when the local operational monitoring daemon is used.

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

**5. Inspect the upgrade log** for any warnings. The log file is written to the working directory from which the wizard was invoked, named `xroad-upgrade-<timestamp>.log` (the bootstrap one-liner places it next to where you ran the `curl ... | sudo bash` command).

## Rollback

The upgrade migrates configuration files and signer state in place, the package set is replaced, and TLS material is copied into the local secret store. Once steps 8–11 have run, the only fully-supported rollback paths are:

**Snapshot restore (recommended).** If you took a host snapshot before the upgrade (LXD, hypervisor, or cloud-provider), restore it. This reverts the filesystem, services, and databases to their pre-upgrade state in one step.

```bash
# LXD example
lxc stop <container>
lxc restore <container> <pre-upgrade-snapshot-name>
lxc start <container>
```

**Restore `/etc/xroad` and the databases from backup.** If no snapshot is available, restore the pre-upgrade Security Server backup (configuration, databases, signer keys) on top of a freshly-reinstalled X-Road 7.8 host, then downgrade or reinstall the V7 packages from a known-good 7.8.x repository. The wizard's automatic `/etc/xroad/xroad-pre-v8-backup-*.tar.gz` covers `/etc/xroad` only — the databases and message log must come from your own backup.

**The wizard halts before destructive change when preflight fails.** Steps 1–7 (version gate, migration-CLI download, /etc/xroad backup, db.properties migration, PostgreSQL preflight, OpenBao repo setup, op-monitor state and country package recording, service stop) all run before the V8 repository switch and package upgrade. If any of them fails, the host can be returned to its pre-upgrade state without a rollback procedure:

- Restore `/etc/xroad/db.properties.bak` over `/etc/xroad/db.properties` if step 4 succeeded but a later step failed.
- Remove the OpenBao sources file added in step 6 if you want to undo the repo change.
- Restart services with `systemctl start xroad-signer xroad-proxy xroad-opmonitor xroad-monitor xroad-proxy-ui-api`.
- `sudo rm -rf /var/lib/xroad-upgrade` resets the sentinel state so a future re-run starts fresh.

Beta 2 does not support a package-level downgrade as a primary rollback strategy.

## Upgrading External Operational Monitoring Daemon Host

An external operational monitoring daemon runs on a host that is not the Security Server. This chapter describes how to upgrade an X-Road 7 external daemon host. To install a new external daemon host, see section 2.9 of the [Security Server Installation Guide](ig-ss_x-road_v8_security_server_installation_guide.md#29-installing-an-external-operational-monitoring-daemon) on Ubuntu or section 2.12 of the [Security Server Installation Guide for RHEL](ig-ss_x-road_v6_security_server_installation_guide_for_rhel.md#212-installing-an-external-operational-monitoring-daemon) on RHEL. The configuration of the daemon, its TLS identity and the exchange of CA certificates are in sections [15.3.1](ug-ss_x-road_6_security_server_user_guide.md#1531-installing-and-configuring-an-external-operational-monitoring-daemon), [15.3.2](ug-ss_x-road_6_security_server_user_guide.md#1532-generating-the-tls-identity-of-an-external-operational-monitoring-daemon) and [15.3.3](ug-ss_x-road_6_security_server_user_guide.md#1533-exchanging-ca-certificates-with-an-external-operational-monitoring-daemon) of the [Security Server User Guide](ug-ss_x-road_6_security_server_user_guide.md). This chapter does not repeat them.

### Upgrading from X-Road 7

This section describes how to upgrade an X-Road 7.8 external operational monitoring daemon host and its Security Server together. The upgrade wizard does not upgrade the external host. X-Road 8 does not read `op-monitor.ini` or `local.ini`, and the daemon does not use the X-Road 7 REST connection and TLS certificates. The external host gets a settings file and a new TLS identity.

The monitoring gap starts in step 1 and ends in step 4. A Security Server that has no running daemon buffers the operational monitoring records in memory. When the buffer is full, the oldest records are dropped. Do steps 2 to 4 without a pause.

**Prerequisites**

- The external host gets the same X-Road 8 version as the Security Server.
- Java 25 is the default Java on the external host. The daemon starts `java` from the `PATH`.
- The external host has PostgreSQL 15 or later on 127.0.0.1.
- You have saved the effective `[op-monitor]` values of the X-Road 7 daemon. Take them from `/etc/xroad/conf.d/local.ini` and, if you edited it, from `/etc/xroad/conf.d/op-monitor.ini`. If a package manager backup copy of `op-monitor.ini` exists, for example `op-monitor.ini.rpmsave`, include its values. The package upgrade does not keep an edited `op-monitor.ini` in use.

**Steps**

1. **External host.** Stop the X-Road 7 daemon and keep it from starting again:

   ```bash
   systemctl disable --now xroad-opmonitor
   ```

2. **Security Server.** Upgrade the Security Server with the wizard, as described in [Upgrade with the wizard](#upgrade-with-the-wizard). Then read the channel host and port that the wizard migrated:

   ```bash
   /usr/share/xroad/scripts/db_property.sh get xroad.common-rpc.channel.op-monitor.host
   /usr/share/xroad/scripts/db_property.sh get xroad.common-rpc.channel.op-monitor.port
   ```

   A command that exits with status 1 has no row: the default value (`127.0.0.1` or `2081`) applies. If the wizard log file has a `[WARN] op-monitor host` line, see [Operational monitoring](#operational-monitoring). The channel host must be an address that the external daemon listens on, and the channel port must be the listening port of the daemon.

3. **External host.** Do these actions in this order:

   1. Switch the X-Road package repository from X-Road 7 to X-Road 8, as in step 8 of the [Manual upgrade procedure](#manual-upgrade-procedure).

   2. **Required: prefix the database keys.** Copy the file `/etc/xroad/db.properties`, then rename the unprefixed `op-monitor.hibernate.*` keys to `xroad.db.op-monitor.hibernate.*`:

      ```bash
      cp -p /etc/xroad/db.properties /root/db.properties.x7
      sed -E -i 's/^([[:space:]]*)op-monitor\.hibernate\./\1xroad.db.op-monitor.hibernate./' /etc/xroad/db.properties
      ```

      Do this before the package upgrade. If you skip it, the upgrade of the `xroad-opmonitor` package on Ubuntu stops with an error. On RHEL the upgrade finishes, but the daemon has a wrong database password. The copy `/root/db.properties.x7` is for a rollback to X-Road 7: restore it over `/etc/xroad/db.properties`.

   3. Upgrade the packages in one transaction that also removes the X-Road 7 `xroad-confclient`. The external host needs no configuration client. On Ubuntu:

      ```bash
      DEBIAN_FRONTEND=noninteractive apt-get install -y --purge xroad-opmonitor xroad-database-local xroad-confclient-
      ```

      On RHEL, write the transaction into a file and run it with `dnf shell`:

      ```bash
      cat > /root/opmonitor-upgrade.dnf <<'EOT'
      remove xroad-confclient
      install xroad-opmonitor xroad-database-local
      run
      EOT
      dnf shell -y /root/opmonitor-upgrade.dnf
      ```

      The upgraded daemon stays disabled and stopped. The transaction does not change `/etc/xroad/db.properties` and keeps the database and its data.

   4. Write the settings file `/etc/xroad/conf.d/op-monitor-settings.yaml` as described in section [15.3.1](ug-ss_x-road_6_security_server_user_guide.md#1531-installing-and-configuring-an-external-operational-monitoring-daemon). Use the saved X-Road 7 values as shown in **Settings carry-over** below.

   5. Generate a new TLS identity of the daemon and install the Security Server RPC CA on the external host, as described in sections [15.3.2](ug-ss_x-road_6_security_server_user_guide.md#1532-generating-the-tls-identity-of-an-external-operational-monitoring-daemon) and [15.3.3](ug-ss_x-road_6_security_server_user_guide.md#1533-exchanging-ca-certificates-with-an-external-operational-monitoring-daemon). The X-Road 7 certificates of the daemon are obsolete. The certificate must carry the channel host from step 2 as a subject alternative name.

   6. Open the firewall of the external host for port 2081 (or the port that you set) from the Security Server. Port 2081 replaces the X-Road 7 port 2080.

   7. Enable and start the daemon:

      ```bash
      systemctl enable --now xroad-opmonitor
      systemctl is-active xroad-opmonitor
      ```

      The second command must print `active`.

4. **Security Server.** Upload the CA of the daemon, as described in section [15.3.3](ug-ss_x-road_6_security_server_user_guide.md#1533-exchanging-ca-certificates-with-an-external-operational-monitoring-daemon). Then restart the proxy and the admin-service:

   ```bash
   service xroad-proxy restart
   service xroad-proxy-ui-api restart
   ```

   The wizard sets `xroad.proxy.addon.op-monitor.enabled` from the state that it recorded in step 6a. The monitoring gap ends when the proxy has restarted and can store records in the daemon.

5. **Security Server.** The wizard may have started the local `xroad-opmonitor` service. Stop it and keep it from starting again:

   ```bash
   systemctl disable --now xroad-opmonitor
   ```

6. **Verify.** In the Security Server user interface, open **Diagnostics** > **Traffic** and run a query. The query must return the records of a test request sent after step 4. On the external host, check that `systemctl is-active xroad-opmonitor` prints `active`. Then delete the key copy:

   ```bash
   rm /root/db.properties.x7
   ```

**Settings carry-over**

Write these X-Road 7 `[op-monitor]` values in the settings file. Do not copy `op-monitor.ini` or `local.ini`: an `.ini` file is not valid as a settings file.

| X-Road 7 setting | Settings file |
|---|---|
| `host` | `xroad.op-monitor.rpc.listen-address` |
| `grpc-port` | `xroad.op-monitor.rpc.port` (2081 by default) |
| `keep-records-for-days`, `clean-interval`, `max-records-in-payload`, `records-available-timestamp-offset-seconds`, `health-statistics-period-seconds` | The same names with the prefix `xroad.op-monitor.` |

The daemon does not use these settings: `port`, `scheme`, `tls-certificate`, `client-tls-certificate` and `xroad-tls-ciphers` (the X-Road 7 REST connection and its TLS), and the `[op-monitor-service]` and `[op-monitor-buffer]` values `socket-timeout-seconds` and `connection-timeout-seconds` (the HTTP timeouts). These settings belong on the Security Server: the channel host and port, `grpc-client-timeout`, and the `[op-monitor-buffer]` values `size`, `max-records-in-message` and `sending-interval-seconds`. The wizard migrates them; `grpc-client-timeout` becomes `xroad.common-rpc.channel.op-monitor.deadline-after`. If you raised the X-Road 7 timeouts, raise `xroad.common-rpc.channel.op-monitor.deadline-after` on the Security Server instead.

**Leftover files**

After the verify step, the X-Road 7 files in this list are no longer used. Remove them if they exist:

```
/etc/xroad/conf.d/*.ini
/etc/xroad/devices.ini
/etc/xroad/configuration-anchor.xml
/etc/xroad/signer            (directory)
/etc/xroad/conf.d/*-logback*.xml
/etc/xroad/conf.d/acme.yml
/etc/xroad/conf.d/mail.yml
/etc/xroad/db.properties.bak
/etc/xroad/ssl/opmonitor.crt
/etc/xroad/ssl/opmonitor.p12
```

Never remove `/etc/xroad/db.properties`, the settings file `/etc/xroad/conf.d/op-monitor-settings.yaml`, `/etc/xroad-opmonitor/ssl` and the Security Server RPC CA file.

**Rows of an earlier migration**

An earlier version of the migration wrote the X-Road 7 `[common] grpc-internal-*` settings to the configuration database of the Security Server as dead rows. They are not used. To remove them, run on the Security Server:

```bash
for k in host tls-enabled keystore keystore-password truststore truststore-password; do
  /usr/share/xroad/scripts/db_property.sh remove "xroad.common.grpc-internal-$k" --yes
done
```

The package `xroad-opmonitor` does not ask for and does not write `xroad.op-monitor.tls.certificate-provisioning.*`. A row of that name from an earlier installation is not used by the daemon.

## Troubleshooting

**Log files:**

- Wizard log: `xroad-upgrade-<timestamp>.log` in the directory where the wizard was invoked (overridable via `XROAD_INSTALLER_LOG_FILE`).
- Migration-CLI output: captured in the wizard log.
- Service logs: `journalctl -u xroad-<service>` and `/var/log/xroad/*.log`.

**Sentinel files (resume after partial failure):**

```bash
ls /var/lib/xroad-upgrade/
```

Each `step-<id>.done` represents a completed migration-CLI sub-step. Delete a specific sentinel to force that sub-step to re-run, or delete the directory to reset everything.

**Common failure patterns:**

| Symptom | Likely cause | Resolution |
|---|---|---|
| `requires X-Road 7.8.x. Detected version: …` | Server is not on 7.8.x | Bring the server to 7.8.x first |
| `migration-cli.jar sha256 verification failed` | Corrupt or partial download, or wrong URL | Confirm `XROAD_MIGRATION_CLI_URL` and re-run; the wizard always re-downloads. |
| `Could not connect to PostgreSQL at … to verify version` | Auth or networking issue against the serverconf DB | Confirm `db.properties` credentials, that PostgreSQL is running, and the configured host/port is reachable |
| `Secret store config not found at /etc/xroad/services/secret-store-local.conf` (TLS migration step) | `xroad-secret-store-local` is not installed or the package install step did not complete | Re-run from the failed step; verify the package was installed by step 9 |
| `Connection to <secret-store> failed` (TLS migration step) | OpenBao is not running or not reachable | Verify `systemctl status` for the OpenBao service and that the host/port in `secret-store-local.conf` is correct |
| `Unattended mode: export XROAD_MIGRATION_SOFTTOKEN_PIN before running` | `keyconf` sub-step needs the soft token PIN | Supply the PIN via env, config file, or env file (see [Secrets in unattended mode](#secrets-in-unattended-mode)) |
| `Migration CLI step '<step>' reported an error: …` | The migration-CLI itself printed a stack trace | Inspect the log; fix the underlying issue (for example a malformed `local.ini`); re-run the wizard — completed sub-steps are skipped |
| `Service <name> did not stop within 60 seconds` | A service is stuck or has long-running connections | Investigate with `systemctl status <name>`; once stopped manually, re-run the wizard |
| `Package manager reported success but xroad-securityserver version did not change` | V8 repository has no package for your CPU architecture, or the repo URL points to a wrong distribution | Verify `XROAD_REPO_BASE_URL` and `XROAD_REPO_MAIN`, confirm packages exist for your `dpkg --print-architecture` / `uname -m` |
| `signer-token-pins` skipped unexpectedly | `xroad-autologin` was not installed | Install `xroad-autologin` first or run the sub-step manually |

**Getting help:**

X-Road is an open source project. Open a GitHub issue at <https://github.com/nordic-institute/X-Road/issues> with the wizard log attached and the contents of `/var/lib/xroad-upgrade/`. <!-- TBD beta 2 discussion URL -->

## References

1. [Security Server Installation Guide for Ubuntu / RHEL](ig-ss_x-road_v8_security_server_installation_guide.md)
2. [Security Server User Guide](ug-ss_x-road_6_security_server_user_guide.md)
3. [X-Road Terms and Abbreviations](../terms_x-road_docs.md)
