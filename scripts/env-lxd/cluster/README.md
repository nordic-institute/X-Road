# LXD Central Server cluster

A two-node Central Server cluster on LXD, built from the packages of this checkout. Both nodes use one PostgreSQL database. Patroni runs PostgreSQL on two hosts (one leader, one streaming replica) and fails over automatically. A haproxy host gives the cluster one name and one database write address.

| Host | Role |
|---|---|
| `xrd-cs1`, `xrd-cs2` | Central Server nodes |
| `xrd-db1`, `xrd-db2` | PostgreSQL with Patroni, and etcd |
| `xrd-cs-lb` | haproxy and the third etcd member |
| `xrd-repo` | package repository, shared with the dev env |

Limits:

- Node 2 stops at OpenBao database creation until the OpenBao join and the Issuer Service database role handover land. It serves no admin API.
- The Linux variant has not been run on a Linux host.

## Start, re-run, tear down

Build the packages first (`scripts/build-native-packages.sh -r resolute`). Run the commands below from the `core` directory. They are for macOS: the default `inventory.yml` reaches LXD in the lima VM `xroad-lxd`. On Linux, see [Linux](#linux).

Standalone cluster (only `xrd-repo` and the cluster hosts):

```
scripts/env-lxd/cluster/start-cluster.sh
```

Dev env with the cluster instead of the single-node `xrd-cs` (Security Servers, `xrd-is`, hurl setup; see [`../README.md`](../README.md#cluster-profile)):

```
cd scripts/env-lxd && ./start-env.sh --profile=cluster
```

Run the same command again to re-run. A run on an unchanged cluster changes nothing. It stops while a database or etcd host is not running: start the host with `fault-step.sh node-start <host>` (`node-thaw` when frozen) and run again.

Tear down:

```
scripts/env-lxd/cluster/delete-cluster.sh
```

This removes the cluster hosts and keeps `xrd-repo`. `scripts/env-lxd/scripts/delete-env.sh` removes every `xrd-*` container.

### Linux

LXD runs directly on the host; no lima VM is used. Install and initialise LXD as described in [`../docs/linux-wsl-setup.md`](../docs/linux-wsl-setup.md). Then use the Linux inventory:

```
CLUSTER_INVENTORY=scripts/env-lxd/cluster/inventory.linux.sample.yml scripts/env-lxd/cluster/start-cluster.sh
```

For the dev env profile, run from `scripts/env-lxd`:

```
CLUSTER_INVENTORY=cluster/inventory.linux.sample.yml ./start-env.sh --profile=cluster \
  --custom-inventory=config/custom/linux-cluster.sample.hosts
```

Copy a sample before you change it: `config/custom/` ignores every file that is not a `*.sample.hosts` file. A user in the `lxd` group needs no `sudo`. If your setup needs it, add `-e onMacOs=yes --ask-become-pass` to `start-cluster.sh`.

## Addresses

| Address | Use |
|---|---|
| `xrd-cs-lb.lxd` | cluster name: the LXD name of `xrd-cs-lb`; haproxy passes ports 80, 6183, 6185 and 6187 to both nodes |
| `xrd-cs-lb.lxd:5432` | PostgreSQL write address: always the Patroni leader |
| `xrd-cs-lb.lxd:5433` | PostgreSQL read-only address: a streaming replica; refuses connections while only the leader runs |
| `https://xrd-cs1.lxd:4000` | Central Server admin UI of node 1; node 2 has none yet |

`start-cluster.sh` prints these at the end.

A node serves the Issuer Service ports only with an Issuer Service TLS certificate. With `cs_ds_tls_throwaway=true` (default) the playbook issues it from a throwaway CA. The dev env profile sets it to `false`, because `setup.hurl` issues the certificate from the test CA.

## Check and fault steps

```
scripts/env-lxd/cluster/check-cluster.sh
scripts/env-lxd/cluster/fault-step.sh [--dry-run] <action> <host> [<service>]
```

`check-cluster.sh` verifies the running cluster against the inventory and exits non-zero on any failure. Each run leaves a row in the table `cluster_probe` of the `postgres` database and the member class `CLUSTERPROBE` in the Central Server.

`fault-step.sh` is the only supported way to change a running cluster. `<host>` is a cluster host name. Each action waits for its state (at most `FAULT_STEP_TIMEOUT` seconds) and fails with a message otherwise. Repeating a node or service action that already reached its state changes nothing and exits 0. `db-switchover` and `db-promote` exit 1 and change nothing when the host already leads. `--dry-run` prints the commands and changes nothing.

| Action | Effect |
|---|---|
| `node-stop <host>` | stop the host |
| `node-start <host>` | start the host and wait for its services |
| `node-freeze <host>` | pause the host; requests to it time out |
| `node-thaw <host>` | resume a frozen host |
| `service-stop <host> <service>` | stop one systemd unit, for example `openbao` |
| `service-start <host> <service>` | start one systemd unit |
| `db-switchover <replica-host>` | planned handover of the database leadership to the replica |
| `db-promote <replica-host>` | forced failover to the replica |

Exit codes: 0 state reached, 1 failed or timed out, 2 unknown action, host or service, or wrong arguments (nothing changed).

What to wait for after a fault (Patroni `ttl` 30 s, `loop_wait` 10 s): the write address works again within 15 s after `db-switchover`, `db-promote` or `node-stop` of the leader, and within 50 s after `node-freeze` of the leader.

The first Central Server admin API change after a failover may need a retry. `check-cluster.sh` waits for Patroni and for that change (see the wait variables below).

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `CLUSTER_INVENTORY` | `inventory.yml` next to the scripts | cluster inventory; read by every script here and by the profile of `start-env.sh`; a relative path is taken from where you start the script |
| `--custom-inventory` | none | `start-env.sh` option: dev env host inventory for the profile |
| `package_source` | `local` | `-e package_source=remote -e xroad_package_repo="deb ..."` takes packages from a remote repository and skips the local package check |
| `cs_ds_tls_throwaway` | `true` | `-e cs_ds_tls_throwaway=false` leaves the Issuer Service TLS certificate to the caller |
| `FAULT_STEP_TIMEOUT` | 180 | seconds each fault step waits |
| `CHECK_PATRONI_WAIT`, `CHECK_CS_WRITE_WAIT` | 120 | seconds `check-cluster.sh` waits for Patroni to converge, and for the admin API (HA node name, then the change) |
| `XROAD_ADMIN_USER`, `XROAD_ADMIN_PASSWORD` | `xrd`, `xroad_cs_admin_password` of the inventory, else the development default | admin login of `check-cluster.sh` |
| `FAULT_STEP_BACKEND`, `CLUSTER_BACKEND_FILE` | `lxd`, none | another backend: a shell file that defines `<name>_*` functions; run with `FAULT_STEP_BACKEND=<name> CLUSTER_BACKEND_FILE=<path>`; the functions are listed in the headers of `cluster-transport.sh` and `fault-step.sh` |

Extra arguments of `start-cluster.sh` go to `ansible-playbook`; the other variables of the inventory (`inventory.yml`) can be set the same way with `-e`.
