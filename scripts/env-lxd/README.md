## LXD based development environment.

This uses native, locally built packages to deploy LXD based environment. LXC is used to manage containers.

**Supported hosts: macOS and Linux (including WSL2 via the Linux path).**

**Remember: most scripts are configurable, refer to their source or --help for more details.**

### Prerequisites

- LXD host: macOS (via Lima VM) or Linux / WSL2 (direct)
- LXC installed on the host

### Usage within macOS

```bash
# Setup macOS host (Lima, LXC, socket_vmnet, ...).
./scripts/setup-mac.sh
# Compile code -> create packages -> deploy.
./start-env.sh
```

`start-env.sh` automatically calls `./scripts/setup-mac-net.sh apply` after the
ansible run so the host can reach LXD containers directly. For the same step on
Linux and WSL2, see [Usage within Linux or WSL2](#usage-within-linux-or-wsl2).

The `lxc` client on the host reaches the LXD API through lima's port forward
`https://127.0.0.1:28443`, not through the VM address. Endpoint security tools that
inspect host-to-VM connections can stall long runs on the VM address; loopback
traffic is not inspected. A VM created before this forward was added to
`config/lima.yaml` gets it with `limactl stop xroad-lxd`, `limactl edit xroad-lxd`
(add the 28443 rule from `config/lima.yaml`) and `limactl start xroad-lxd`, or by
recreating it with `./scripts/setup-mac.sh --recreate`, which also deletes all
containers. Then run
`./scripts/setup-lxc.sh` once to move the `xroad-lxd` remote; later runs change
nothing.

### Usage within Linux or WSL2

Linux and WSL2 run LXD directly — no Lima VM is needed. You supply a custom
inventory that points `lxd_servers` at `localhost` instead of a Lima SSH target.

A ready-to-use sample inventory is at `config/custom/linux.sample.hosts`.

```bash
cp config/custom/linux.sample.hosts config/custom/my-inventory.txt
./start-env.sh --custom-inventory=config/custom/my-inventory.txt
```

For full setup instructions (LXD initialisation, group membership, DNS routing,
WSL2-specific notes) see [`docs/linux-wsl-setup.md`](docs/linux-wsl-setup.md).

`start-env.sh` picks the right network script by OS via its `applyHostNet` helper;
`stop-env.sh` and `scripts/delete-env.sh` call the matching `cleanup` so
the host's resolver doesn't linger pointing at a stopped bridge.

Common URLs after a successful start:

- Central Server admin UI: <https://xrd-cs.lxd:4000/>
- Security Server admin UI (per SS): <https://xrd-ss0.lxd:4000/>, <https://xrd-ss1.lxd:4000/>
- Test CA web: <http://xrd-ca.lxd:8888/testca/>
- Configuration Proxy: <http://xrd-cp.lxd/>

### Cluster profile

`./start-env.sh --profile=cluster` replaces the single-node Central Server `xrd-cs` with the two-node Central Server cluster described in [`cluster/README.md`](cluster/README.md). Use it to develop and test against a Central Server on a shared, highly available database. Without `--profile`, or with `--profile=single`, nothing changes: the single-node dev env starts as before, with Jaeger off by default. The cluster profile also starts with Jaeger off.

The profile needs an LXD host with at least 20 GiB memory. On macOS the LXD host is the lima VM. `start-env.sh` stops when the VM has less memory. Create it with `./scripts/setup-mac.sh --memory=20`.

The profile starts the dev env as usual, without `xrd-cs`: test CA, Security Servers, Information System host `xrd-is`, hurl host and the package repository `xrd-repo`. It then runs `cluster/start-cluster.sh` for the cluster, and `setup.hurl` initialises the Central Server through node 1.

Containers:

| Container | Role |
|---|---|
| `xrd-cs1`, `xrd-cs2` | Central Server nodes |
| `xrd-db1`, `xrd-db2` | PostgreSQL hosts (Patroni) |
| `xrd-cs-lb` | haproxy host; its LXD name `xrd-cs-lb.lxd` is the cluster name |

In the profile:

- The Central Server admin UI and API are on node 1: <https://xrd-cs1.lxd:4000/>. Port 4000 is per node; node 2 serves no admin API while its install stops at OpenBao database creation.
- The Issuer Service host is the cluster name. Every container and the host resolve it.
- `setup.hurl` issues the Issuer Service TLS certificate from the test CA. The cluster's throwaway CA is not used.
- The Security Servers send mail to Mailpit on `xrd-cs1`. Node 1 and node 2 get the dev `local.conf` and Mailpit, like `xrd-cs` in the single-node env.
- The configuration anchor lists node 1's address, not the cluster name.

Lifecycle:

- `--recreate --profile=cluster` removes the dev env and the cluster, then rebuilds.
- Starting one profile while containers of the other exist stops before any change and asks for `--recreate`.
- A second `./start-env.sh --profile=cluster --skip-build` leaves the cluster unchanged.
- `scripts/delete-env.sh` removes every `xrd-*` container, the cluster included. `cluster/delete-cluster.sh` removes only the cluster containers.
- To redeploy a module to the cluster nodes, use `./dev.sh -i cluster/deploy-inventory.yml -bdm <module>`. It targets node 1 only.

Configuration:

- `CLUSTER_INVENTORY` names the cluster inventory (default `cluster/inventory.yml`). `start-env.sh` turns a relative path into an absolute one and passes it to every cluster script it runs.
- `--custom-inventory` is allowed with the profile. It replaces the dev env host inventory (`config/ansible_hosts_cluster.txt`); `CLUSTER_INVENTORY` still names the cluster.
- On Linux or WSL2, use the Linux samples. Run from `scripts/env-lxd`:

  ```bash
  CLUSTER_INVENTORY=cluster/inventory.linux.sample.yml ./start-env.sh --profile=cluster \
    --custom-inventory=config/custom/linux-cluster.sample.hosts
  ```

  The Linux variant has not yet been run on a Linux host. See [Linux](cluster/README.md#linux).
- `SMOKE_HOST` is the host name that `setup-mac-net.sh` and `setup-linux-net.sh` resolve to check the host networking. The default is `xrd-cs`; the profile sets `xrd-cs1`, because `xrd-cs` does not exist in it.
- The single profile (without `--profile=cluster`) refuses to start when a cluster container exists. This check is skipped with `--recreate` or `--bust-cache`. When `start-env.sh` cannot list the cluster containers for it (for example the inventory is unreadable), it stops with an error and does not skip the check.

What does not work in the profile:

- The e2e Java suite: it expects `xrd-cs`.
- Snapshots: `--snapshot-empty-containers` is refused.
- Node 2 does not complete: its install stops at OpenBao database creation until the OpenBao join and the Issuer Service database handover exist.
- The cluster and `xrd-cs` cannot run side by side.

### Monitoring

Host-level monitoring is provided by [Netdata](https://www.netdata.cloud/), installed
via snap on the LXD host (the Lima VM on macOS, or the Linux host directly). Netdata
auto-discovers every `xrd-*` container as a cgroup — no agent runs inside the X-Road
containers.

**Enabled by default.** Disable for a run by exporting `ENABLE_NETDATA=false`:

```bash
ENABLE_NETDATA=false ./start-env.sh
```

Or pass it directly to ansible:

```bash
ANSIBLE_CONFIG=config/ansible.cfg ansible-playbook \
  -i config/ansible_hosts.txt \
  ../../development/ansible/xroad_dev.yml \
  --skip-tags netdata
# or
ANSIBLE_CONFIG=config/ansible.cfg ansible-playbook \
  -i config/ansible_hosts.txt \
  ../../development/ansible/xroad_dev.yml \
  -e enable_netdata=false
```

Once installed, open the dashboard from the host browser. Netdata listens on
the lima VM, reachable directly via the same `192.168.105.0/24` route as
container traffic:

- URL: <http://192.168.105.2:3999>
- "Containers" / cgroups section: per-container CPU, memory, disk, and network for
  `xrd-cs`, `xrd-ss0`, `xrd-ss1`, `xrd-ca`, `xrd-cp`, `xrd-is`, `xrd-hurl`
- "Applications" tab: top processes per cgroup
- API health check: `curl -s http://192.168.105.2:3999/api/v1/info | jq '.version, .cloud_enabled'`
  → expect a version string and `false` (Netdata Cloud is disabled — no telemetry leaves the host).

Footprint is roughly 100–200 MB RSS at a 2 s scrape interval.

### Tracing (Jaeger)

Jaeger v2 runs in its own `xrd-jaeger` LXD container, collecting OTLP traces
emitted by X-Road services. Membership is controlled via the inventory rather
than a role flag.

**Disabled by default** to save memory; Netdata covers host monitoring. To
enable, uncomment the host in your inventory (`config/ansible_hosts.txt`,
`config/ansible_hosts_cluster.txt` or your custom inventory):

```ini
[jaeger_servers]
xrd-jaeger ansible_connection=lxd
```

When the group is empty, the jaeger play is a no-op, `init-lxd` does not
provision the container, and `otel.conf` sets `OTEL_SDK_DISABLED=true` so
services do not export traces.

Jaeger UI: <http://xrd-jaeger.lxd/>
