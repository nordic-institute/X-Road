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
ansible run so the host can reach LXD containers directly (see
[Direct host → container networking](#direct-host--container-networking)).

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

**Enabled by default.** To disable, comment out the host in your inventory
(`config/ansible_hosts.txt` or your custom inventory):

```ini
# [jaeger_servers]
# xrd-jaeger ansible_connection=lxd
```

When the group is empty, the jaeger play is a no-op and `init-lxd` will not
provision the container. Note: `init-dev-configuration` still writes an
`otel.conf` pointing at `xrd-jaeger.lxd`; with the container absent, the
endpoint is unreachable and trace export silently fails — services keep
running.

Jaeger UI: <http://xrd-jaeger.lxd/>
