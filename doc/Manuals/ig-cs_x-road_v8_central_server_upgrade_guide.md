# Central Server Upgrade Guide: X-Road 7.8 to X-Road 8 <!-- omit in toc -->

**X-ROAD 8**

Version: 1.2
Doc. ID: IG-CS-8-UPGRADE

---

## Version history <!-- omit in toc -->

| Date       | Version | Description                                    | Author             |
|------------|---------|------------------------------------------------|--------------------|
| 19.05.2026 | 1.0     | Initial version                                | Egidijus Milierius |
| 11.11.2026 | 1.1     | Carry the node name before the package upgrade | Ričardas Bučiūnas  |
| 12.11.2026 | 1.2     | Keep db.properties; add `--installation=cs`    | Ričardas Bučiūnas  |

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
| Java | Java 25 must be installable from the OS package repositories (the X-Road 8 packages depend on it). Step 8 installs it before the package upgrade, because `migration-cli.jar` needs it. |
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

**3. Back up db.properties.** Keep a `.bak` of the original. Do not edit `/etc/xroad/db.properties`: X-Road 8 reads it in the same format as X-Road 7.8.

```bash
cp -p /etc/xroad/db.properties /etc/xroad/db.properties.bak
```

**4. Verify PostgreSQL version.** Read the `centerui` JDBC URL from the `spring.datasource.url` key in `/etc/xroad/db.properties`. Read the `psql` user and password from the `spring.datasource.username` and `spring.datasource.password` keys in the same file. Then connect:

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

**8. Carry the node name.** Install Java 25 on the X-Road 7.8 host. `migration-cli.jar`, staged in [Before you start](#before-you-start), is compiled for Java 25, and the X-Road 7.8 packages install only Java 21.

```bash
apt-get install -y openjdk-25-jre-headless
java -version 2>&1 | head -n 1
```

The first line of the output must report version 25. If it reports another version, run `update-alternatives --config java`, choose the Java 25 entry and check again.

Then carry the node name from X-Road 7 into `node.ini`. The option `--installation=cs` tells the tool that the host is a Central Server. The tool then reads the `spring.datasource.*` keys of `db.properties`, and it refuses sub-commands that apply only to a Security Server.

```bash
java -jar /var/tmp/migration-cli.jar node-name --installation=cs /etc/xroad/conf.d/local.ini /etc/xroad/conf.d/node.ini
[ -f /etc/xroad/conf.d/node.ini ] && chown xroad:xroad /etc/xroad/conf.d/node.ini
```

Run the `node-name` sub-command before the package upgrade in step 9, so that the package scripts, the TLS step and the first start of the Central Server services see the node name. X-Road 7.8 keeps the node name in `[center] ha-node-name` of `/etc/xroad/conf.d/local.ini`. X-Road 8 reads it from the `name` key of the `[node]` section in `/etc/xroad/conf.d/node.ini`. The sub-command creates `node.ini` if it does not exist and keeps its other keys, such as `[node] type`. It changes nothing when `ha-node-name` is empty or missing, and when `node.ini` already holds the same name. It stops without writing anything when the name is not valid (it must start with a letter or a digit, contain only letters, digits, `_` and `-`, and be at most 63 characters long, so a host name with dots is not valid) or when `node.ini` holds a different name. If the name is not valid, choose a valid name and set it in `[node] name` of `node.ini` yourself. The database rows keyed by the old name stay behind under that name. The X-Road 7.8 package scripts give the `xroad` user ownership of the files in `/etc/xroad/conf.d`. The `chown` line gives a newly created `node.ini` the same owner.

**9. Upgrade the Central Server packages.** The package scripts may start the X-Road services. A service that starts now uses the node name from step 8. Stop these services again after the installation.

```bash
DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-centralserver
```

Verify the version actually changed:

```bash
dpkg-query -W -f='${Version}\n' xroad-center
```

The output must now begin with `8.`.

Stop every active X-Road service except `xroad-secret-store-local`, which step 10 needs:

```bash
mapfile -t SERVICES < <(systemctl list-units --type=service --state=active 'xroad-*' \
  --no-legend --plain | awk '{print $1}' | grep -v '^xroad-secret-store-local\.service$')
for s in "${SERVICES[@]}"; do
  systemctl stop "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" || break; sleep 2; done
done
```

**10. Migrate TLS material to the local secret store.** Source `/etc/xroad/services/secret-store-local.conf` to get `XROAD_SECRET_STORE_{SCHEME,HOST,PORT,TOKEN}`, then for each existing pair `/etc/xroad/ssl/{internal,center-admin-service,management-service}.{crt,key}` write a JSON `{"certificate": "...", "privateKey": "..."}` payload to `POST <secret-store>/v1/xrd-secret/tls/<path>`, where `<path>` is `internal`, `admin-service/<node>` (for `center-admin-service`), or `management-service/<node>`.

`<node>` is the name of this Central Server node. Read it from the `name` key of the `[node]` section in `/etc/xroad/conf.d/node.ini`, which step 8 filled in from X-Road 7. If the key is not set, use `node_0`. The name must start with a letter or a digit, contain only letters, digits, `_` and `-`, and be at most 63 characters long. For example, the node name `node_1` gives the admin service key path `tls/admin-service/node_1`.

The Security Server wizard script `migrate_tls_to_secret_store.sh` shows the same request format, but it writes to the Security Server paths `tls/admin-service` and `tls/management-service`. Do not copy its paths: the Central Server reads `tls/admin-service/<node>` and `tls/management-service/<node>`.

The migrated user interface and management services certificates keep their X-Road 7 names. To get certificates with the names stored in the configuration database, delete the key and restart the services as described in [IG-CS](ig-cs_x-road_8_central_server_installation_guide.md#262-provisioning-tls-certificates-for-management-services-and-user-interface) section 2.6.2. After the management services certificate is replaced, the management Security Server must trust the new certificate.

**11. Run migration-CLI sub-steps.** The migration-CLI is shared between Security Server and Central Server. A sub-command exits with an error when its source file is missing. The `[ -f ]` checks below skip the optional ones. `keyconf` always needs `/etc/xroad/signer/keyconf.xml`. Pass `--installation=cs` to every sub-command. For a Central Server, the following sub-commands are the relevant ones:

```bash
java -jar /var/tmp/migration-cli.jar validate --installation=cs

# devices.ini — HSM/signer module declarations
[ -f /etc/xroad/devices.ini ] && \
  java -jar /var/tmp/migration-cli.jar signer-devices --installation=cs /etc/xroad/devices.ini /etc/xroad/db.properties

# ini-to-db — every override-*.ini, then local.ini
for ini in /etc/xroad/conf.d/override-*.ini /etc/xroad/conf.d/local.ini; do
  [ -f "$ini" ] && java -jar /var/tmp/migration-cli.jar ini-to-db --installation=cs "$ini" /etc/xroad/db.properties
done

# <node> is the node name from step 10; --node may be left out only for node_0

# keyconf — signer state (soft token PIN required)
XROAD_MIGRATION_SOFTTOKEN_PIN='<pin>' \
  java -jar /var/tmp/migration-cli.jar keyconf --installation=cs --node <node> /etc/xroad/signer /etc/xroad/db.properties

# signer-token-pins — only if xroad-autologin is installed
if [ -f /usr/share/xroad/autologin/custom-fetch-pin.sh ] \
   || [ -f /usr/share/xroad/autologin/default-fetch-pin.sh ]; then
  java -jar /var/tmp/migration-cli.jar signer-token-pins --installation=cs --node <node>
fi
```

With `--node <node>`, `keyconf` writes the signer keys and certificates under the node name, so the signer on this node sees only its own rows, and `signer-token-pins` stores the PINs in the secret store under `signer/token-pins/<node>/<tokenId>`. Without `--node`, the node name is `node_0`. If the name is not valid, migration-CLI stops before it writes anything. Tokens that already have a PIN under this node name are skipped, so it is safe to run the step again.

Review the `keyconf` output. A ⚠ line that says `already exists` is expected when you run the step again. Act only on lines marked ❌ and on lines that say a key was skipped. Such a line means that a soft token key file was not migrated; `keyconf` still exits with status 0. To restore the key, put `<keyId>.p12` back under `/etc/xroad/signer/softtoken/` and run `keyconf` again. Running `keyconf` again is safe: it saves only the items that are not yet in the database.

Stop and investigate when any command exits with a non-zero status. A failed sub-command often prints a Java stack trace too. Do not proceed if a command printed an exception. The Security Server wizard looks for the patterns `Error `, `Caused by:`, `Exception in ` and `<Class>Exception:` in the output, and the same patterns are reliable signals here.

**12. Start Central Server services.**

```bash
for s in xroad-signer xroad-center xroad-center-management-service xroad-center-registration-service xroad-monitor xroad-opmonitor xroad-ds-issuer-service; do
  systemctl cat "$s" >/dev/null 2>&1 || { echo "skip $s (not installed)"; continue; }
  systemctl start "$s"
  for _ in {1..30}; do systemctl is-active --quiet "$s" && break; sleep 2; done
done
```

**13. Clean up obsolete V7 config files** once you have verified the upgrade (see [Post-upgrade verification](#post-upgrade-verification)). Keep `/etc/xroad/conf.d/node.ini`; it holds the node name:

```
/etc/xroad/conf.d/*.ini      (all except node.ini)
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

Expect: `xroad-signer`, `xroad-center`, `xroad-center-management-service`, `xroad-center-registration-service`, `xroad-monitor`, `xroad-ds-issuer-service`, plus any optional services that were active before the upgrade.

**2. Confirm the package version:**

```bash
dpkg-query -W -f='${Package} ${Version}\n' 'xroad-*'
```

Every X-Road package should be on `8.x`.

**3. Open the Central Server admin UI** at `https://<server>:4000/` and sign in. Verify that the member list, global configuration sources, trusted anchors, and approved CAs/timestamping services are intact.

**4. Verify global configuration distribution.** Trigger or wait for the configuration distribution cycle and confirm that Security Servers in your federation can still fetch global configuration from this Central Server.

**5. Verify member registration and management endpoints respond.** Use the standard probes for the management and registration web services.

## Rollback

The upgrade is destructive once steps 7 (repo switch) and 9 (package install) have run. Once the migration-CLI has executed against the `centerui` database, the schema and configuration data are V8.

**Snapshot restore (recommended).** Restore the host snapshot taken in [Before you start](#before-you-start).

**Restore /etc/xroad and the centerui database from backup.** If no snapshot is available, restore the pre-upgrade backup on top of a freshly-reinstalled 7.8 Central Server. The `/etc/xroad/xroad-pre-v8-backup-*.tar.gz` written in step 2 covers `/etc/xroad/` only; the database must come from your standard CS backup. The `node-name` sub-command in step 8 may have written `/etc/xroad/conf.d/node.ini`; the backup does not contain it, so delete that file when you restore over a tree that already has it.

**The procedure halts before destructive change when preflight fails.** Steps 1–6 can be undone without a rollback procedure: remove the OpenBao sources file, and `systemctl start` the services. Beta 2 does not support a package-level downgrade as a primary rollback strategy.

**The package installation in step 9 changes `/etc/xroad/db.properties`.** Steps 1–6 do not change it. To get the original file back, copy `/etc/xroad/db.properties.bak` from step 3 over it.

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

X-Road is an open source project. Open a GitHub issue at <https://github.com/nordic-institute/X-Road/issues> with the migration-CLI output attached. If a `db.properties` file is needed, remove the database password from it first. <!-- TBD beta 2 discussion URL -->

## References

1. [Central Server Installation Guide](ig-cs_x-road_8_central_server_installation_guide.md)
2. [Security Server Upgrade Guide](ig-ss_x-road_v8_security_server_upgrade_guide.md)
3. [Central Server High Availability Installation Guide](ig-csha_x-road_6_ha_installation_guide.md)
4. [X-Road Terms and Abbreviations](../terms_x-road_docs.md)
