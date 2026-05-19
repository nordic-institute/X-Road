# Central Server Upgrade Guide: X-Road 7.8 to X-Road 8 <!-- omit in toc -->

**X-ROAD 8**

Version: 1.0
Doc. ID: IG-CS-8-UPGRADE

---

## Version history <!-- omit in toc -->

| Date       | Version | Description     | Author             |
|------------|---------|-----------------|--------------------|
| 19.05.2026 | 1.0     | Initial version | Egidijus Milierius |

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
- [Manual upgrade procedure](#manual-upgrade-procedure)
- [Post-upgrade verification](#post-upgrade-verification)
- [Rollback](#rollback)
- [Troubleshooting](#troubleshooting)
- [References](#references)

## Overview

This guide walks you through upgrading an existing X-Road 7.8 Central Server to X-Road 8 (beta 2). There is no Central Server upgrade wizard for beta 2; the procedure documented here drives `migration-cli.jar` and the package manager directly.

### What this guide covers

- Single-node Central Server instances installed from native packages (DEB on Ubuntu)
- Step-by-step manual procedure
- Post-upgrade verification
- Rollback options

### What this guide does not cover

- **Security Server upgrades** — see [Security Server Upgrade Guide](ig-ss_x-road_v8_security_server_upgrade_guide.md)
- **Central Server HA (cluster) upgrades** — see the [Central Server High Availability Installation Guide](ig-csha_x-road_6_ha_installation_guide.md) for the existing cluster topology; the X-Road 8 HA upgrade workflow is **not yet documented for beta 2**. A clustered Central Server requires shared-database coordination during upgrade — running this single-node procedure independently on every node would conflict with the replicated `centerui` database.
- Upgrade from X-Road versions older than 7.8.x — must first be brought to 7.8.x

### Disclaimer

This document applies to X-Road 8 Beta 2. Pre-release software may behave differently from the final X-Road 8.0 release. **Central Server upgrades on beta 2 are operator-driven**; gaps in the procedure may exist and should be reported as GitHub issues.

## Prerequisites

| Requirement | Notes |
|---|---|
| Operating system | Ubuntu Server 22.04 / 24.04 LTS |
| Current X-Road version | 7.8.x (any patch level). Earlier versions must first be upgraded to 7.8.x. |
| PostgreSQL major version | 15 or newer for the `centerui` database |
| Java | Java 25 must be installable from the OS package repositories (the X-Road 8 packages depend on it) |
| Network reachability | Outbound HTTPS to `https://artifactory.niis.org` (X-Road 8 packages, GPG key, migration CLI) and `https://pkgs.openbao.org` (OpenBao packages) |
| Root / sudo access | Required for all steps |
| Backup | A current backup of the Central Server (configuration, `centerui` database, signer keys) is **mandatory** |

Verify the current version:

```bash
dpkg-query -W -f='${Version}\n' xroad-center
```

The output must begin with `7.8.`.

## Before you start

1. **Take a full backup.** Use the standard Central Server backup tooling, capture both the `centerui` database and `/etc/xroad/`. Copy the archive off the host. There is no automatic pre-upgrade snapshot for the Central Server upgrade.
2. **Take a host snapshot if possible** (LXD, hypervisor, cloud-provider). This is the only fully-supported rollback path — see [Rollback](#rollback).
3. **Notify federated members.** Global configuration distribution will be unavailable while the Central Server is stopped. Plan a maintenance window.
4. **Stage the migration-CLI artifact:**
   ```bash
   curl -fsSL https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar       -o /var/tmp/migration-cli.jar
   curl -fsSL https://artifactory.niis.org/xroad-scripts/0.0.1-beta/migration-cli.jar.sha256 -o /var/tmp/migration-cli.jar.sha256
   ( cd /var/tmp && awk '{print $1"  migration-cli.jar"}' migration-cli.jar.sha256 | sha256sum -c - )
   chmod 600 /var/tmp/migration-cli.jar
   ```

## Manual upgrade procedure

Run all commands as root.

**1. Verify version.**

```bash
dpkg-query -W -f='${Version}\n' xroad-center
```

Confirm the output begins with `7.8.`. Stop here if it does not.

**2. Snapshot /etc/xroad.**

```bash
TS=$(date +%Y-%m-%d_%H%M%S)
tar -czf "/var/tmp/xroad-pre-v8-backup-${TS}.tar.gz" -C /etc xroad
chmod 600 "/var/tmp/xroad-pre-v8-backup-${TS}.tar.gz"
mv "/var/tmp/xroad-pre-v8-backup-${TS}.tar.gz" /etc/xroad/
```

**3. Migrate db.properties to V8 format.** Prefix every `key = value` line with `xroad.db.` (comments, blank lines, and already-prefixed lines pass through). Keep a `.bak` of the original.

```bash
cp -p /etc/xroad/db.properties /etc/xroad/db.properties.bak
sed -E -i '/^[[:space:]]*([#!]|xroad\.db\.|$)/b; s/^([[:space:]]*)([^=[:space:]]+)([[:space:]]*=)/\1xroad.db.\2\3/' \
  /etc/xroad/db.properties
```

**4. Verify PostgreSQL version.** Read the `centerui` JDBC URL from `/etc/xroad/db.properties` (the V7 key was `centerui.hibernate.connection.url`; after step 3 it is `xroad.db.centerui.hibernate.connection.url`) and connect:

```bash
PGPASSWORD='<centerui-password>' \
  psql -w -h <host> -p <port> -U <centerui-user> -d postgres \
  -tAc 'SHOW server_version_num'
```

The numeric server version divided by 10000 must be ≥ 15.

**5. Configure the OpenBao package repository.**

```bash
curl -fsSL https://openbao.org/assets/openbao-gpg-pub-20240618.asc \
  -o /usr/share/keyrings/openbao-keyring.asc
echo "deb [signed-by=/usr/share/keyrings/openbao-keyring.asc] https://pkgs.openbao.org/deb stable main" \
  > /etc/apt/sources.list.d/openbao.list
apt-get update
```

**6. Stop all Central Server services.**

```bash
mapfile -t SERVICES < <(systemctl list-units --type=service --state=active 'xroad-*' \
  --no-legend --plain | awk '{print $1}')
for s in "${SERVICES[@]}"; do
  systemctl stop "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" || break; sleep 2; done
done
```

The Central Server service set typically includes `xroad-center`, `xroad-center-management-service`, `xroad-center-registration-service`, `xroad-signer` and `xroad-nginx`.

**7. Switch the X-Road package repository from V7 to V8.**

```bash
TS=$(date +%Y%m%d-%H%M%S)
mv /etc/apt/sources.list.d/xroad.list /etc/apt/sources.list.d/xroad.list.v7.bak."$TS"
curl -fsSL https://artifactory.niis.org/api/gpg/key/public \
  -o /usr/share/keyrings/xroad-keyring.asc
CODENAME=$(lsb_release -sc)
echo "deb [signed-by=/usr/share/keyrings/xroad-keyring.asc] https://artifactory.niis.org/xroad8-snapshot-deb ${CODENAME}-current main" \
  > /etc/apt/sources.list.d/xroad.list
apt-get update
```

**8. Upgrade the Central Server packages.**

```bash
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-centralserver
```

Verify the version actually changed:

```bash
dpkg-query -W -f='${Version}\n' xroad-center
```

The output must now begin with `8.`.

**9. Migrate TLS material to the local secret store.** Source `/etc/xroad/services/secret-store-local.conf` to get `XROAD_SECRET_STORE_{SCHEME,HOST,PORT,TOKEN}`, then for each existing pair `/etc/xroad/ssl/{internal,center-admin-service,management-service}.{crt,key}` write a JSON `{"certificate": "...", "privateKey": "..."}` payload to `POST <secret-store>/v1/xrd-secret/tls/<path>`, where `<path>` is `internal`, `admin-service` (for `center-admin-service`), or `management-service`.

The pattern matches what the Security Server wizard does in `migrate_tls_to_secret_store.sh`; if you want a working reference, read that script.

**10. Run migration-CLI sub-steps.** The migration-CLI is shared between Security Server and Central Server — each sub-command skips cleanly when its source file is absent, so the operator's job is simply to run the full sequence in order. For a Central Server, the following sub-commands are the relevant ones:

```bash
java -jar /var/tmp/migration-cli.jar validate

# devices.ini — HSM/signer module declarations
[ -f /etc/xroad/devices.ini ] && \
  java -jar /var/tmp/migration-cli.jar signer-devices /etc/xroad/devices.ini /etc/xroad/db.properties

# ini-to-db — every override-*.ini, then local.ini
for ini in /etc/xroad/conf.d/override-*.ini /etc/xroad/conf.d/local.ini; do
  [ -f "$ini" ] && java -jar /var/tmp/migration-cli.jar ini-to-db "$ini" /etc/xroad/db.properties
done

# keyconf — signer state (soft token PIN required)
XROAD_MIGRATION_SOFTTOKEN_PIN='<pin>' \
  java -jar /var/tmp/migration-cli.jar keyconf /etc/xroad/signer /etc/xroad/db.properties

# signer-token-pins — only if xroad-autologin is installed
if [ -f /usr/share/xroad/autologin/custom-fetch-pin.sh ] \
   || [ -f /usr/share/xroad/autologin/default-fetch-pin.sh ]; then
  java -jar /var/tmp/migration-cli.jar signer-token-pins
fi
```

**Treat any Java stack trace as a failure even if the process exits 0** — do not proceed if a step printed an exception. The migration-CLI scans for `Error `, `Caused by:`, `Exception in `, and `<Class>Exception:` patterns when run from the Security Server wizard, so the same patterns are reliable signals here.

**11. Start Central Server services.**

```bash
for s in xroad-signer xroad-center xroad-center-management-service xroad-center-registration-service xroad-monitor xroad-opmonitor; do
  systemctl cat "$s" >/dev/null 2>&1 || { echo "skip $s (not installed)"; continue; }
  systemctl start "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" && break; sleep 2; done
done
```

**12. Clean up obsolete V7 config files** once you have verified the upgrade (see [Post-upgrade verification](#post-upgrade-verification)):

```
/etc/xroad/conf.d/*.ini
/etc/xroad/devices.ini
/etc/xroad/signer            (directory)
/etc/xroad/conf.d/*-logback*.xml
/etc/xroad/db.properties.bak
```

## Post-upgrade verification

**1. Check all expected services are active:**

```bash
systemctl list-units --type=service --state=active 'xroad-*'
```

Expect: `xroad-signer`, `xroad-center`, `xroad-center-management-service`, `xroad-center-registration-service`, `xroad-monitor`, plus any optional services that were active before the upgrade.

**2. Confirm the package version:**

```bash
dpkg-query -W -f='${Package} ${Version}\n' 'xroad-*'
```

Every X-Road package should be on `8.x`.

**3. Open the Central Server admin UI** at `https://<server>:4000/` and sign in. Verify that the member list, global configuration sources, trusted anchors, and approved CAs/timestamping services are intact.

**4. Verify global configuration distribution.** Trigger or wait for the configuration distribution cycle and confirm that Security Servers in your federation can still fetch global configuration from this Central Server.

**5. Verify member registration and management endpoints respond.** Use the standard probes for the management and registration web services.

## Rollback

The upgrade is destructive once steps 7 (repo switch) and 8 (package install) have run. Once the migration-CLI has executed against the `centerui` database, the schema and configuration data are V8.

**Snapshot restore (recommended).** Restore the host snapshot taken in [Before you start](#before-you-start).

**Restore /etc/xroad and the centerui database from backup.** If no snapshot is available, restore the pre-upgrade backup on top of a freshly-reinstalled 7.8 Central Server. The `/etc/xroad/xroad-pre-v8-backup-*.tar.gz` written in step 2 covers `/etc/xroad/` only; the database must come from your standard CS backup.

**The procedure halts before destructive change when preflight fails.** Steps 1–6 can be undone without a rollback procedure: restore `/etc/xroad/db.properties.bak`, remove the OpenBao sources file, and `systemctl start` the services. Beta 2 does not support a package-level downgrade as a primary rollback strategy.

## Troubleshooting

**Log files:**

- Migration-CLI output: stdout/stderr where you ran it.
- Service logs: `journalctl -u xroad-<service>` and `/var/log/xroad/*.log`.

**Common failure patterns:**

| Symptom | Likely cause | Resolution |
|---|---|---|
| `requires X-Road 7.8.x. Detected version: …` | Server is not on 7.8.x | Bring the server to 7.8.x first |
| `migration-cli.jar sha256 verification failed` | Corrupt or partial download | Re-download the JAR and re-verify |
| `Could not connect to PostgreSQL at …` | Auth or networking issue against the centerui DB | Confirm `db.properties` credentials, that PostgreSQL is running, and the configured host/port is reachable |
| `Connection to <secret-store> failed` (TLS migration step) | OpenBao is not running or not reachable | Verify `systemctl status` for the OpenBao service and the host/port in `/etc/xroad/services/secret-store-local.conf` |
| `Migration CLI step '<step>' reported an error: …` | The migration-CLI printed a stack trace | Inspect the log; fix the underlying issue; re-run only the failed sub-command |
| `Package manager reported success but xroad-center version did not change` | V8 repository has no package for your architecture, or wrong repo URL | Verify the repo configuration and confirm packages exist for your architecture |

**Getting help:**

X-Road is an open source project. Open a GitHub issue at <https://github.com/nordic-institute/X-Road/issues> with the migration-CLI output and your `/etc/xroad/db.properties.bak` attached. <!-- TBD beta 2 discussion URL -->

## References

1. [Central Server Installation Guide](ig-cs_x-road_8_central_server_installation_guide.md)
2. [Security Server Upgrade Guide](ig-ss_x-road_v8_security_server_upgrade_guide.md)
3. [Central Server High Availability Installation Guide](ig-csha_x-road_6_ha_installation_guide.md)
4. [X-Road Terms and Abbreviations](../terms_x-road_docs.md)
