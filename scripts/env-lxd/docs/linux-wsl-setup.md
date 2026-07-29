# Linux and WSL2 setup for the LXD stack

On Linux (including WSL2), LXD runs directly on the host — no Lima VM is needed.
The stack uses the same `start-env.sh` and Ansible playbooks as macOS, but with a
local inventory that points `lxd_servers` at `localhost` instead of a Lima SSH target.

This is not a separate code path. It is the existing Linux branch of `start-env.sh`
with the inventory replaced.

---

## Prerequisites

- **LXD** installed and initialised (`lxd init`). Tested with LXD 5.x and 6.x.
- **lxc** client on PATH (typically bundled with LXD).
- **Ansible** (≥2.14) with `ansible.posix` collection (`ansible-galaxy collection install ansible.posix`).
- **Python 3** on PATH (`ansible_python_interpreter = auto_silent` in `config/ansible.cfg`).
- **jq**, **dig** or **nslookup** (used by `scripts/setup-linux-net.sh`).
- Sufficient resources: ≥8 GB RAM, ≥20 GB free disk.

On WSL2: LXD must be installed inside the WSL2 distribution (not Windows-side).
WSL2 kernels from Ubuntu 22.04+ ship the required cgroup namespacing. Confirm with
`lxd --version`.

---

## One-time setup

### 1. Initialise LXD

```bash
sudo lxd init --auto
# Or run `sudo lxd init` for the interactive wizard (recommended first time).
```

Verify the default bridge is up:

```bash
ip link show lxdbr0
```

The bridge IP defaults to `10.10.10.1/24`. The scripts expect this subnet for DNS
routing — if your `lxd init` chose a different subnet, update `LXD_DNS_IP` in
`scripts/setup-linux-net.sh` accordingly.

### 2. Add your user to the `lxd` group

```bash
sudo usermod -aG lxd "$USER"
newgrp lxd   # activate without re-login
```

### 3. Create a custom inventory

Copy the sample inventory and adjust if needed:

```bash
cp config/custom/linux.sample.hosts config/custom/my-inventory.txt
```

The sample sets `ansible_connection=local` for the `lxd_servers` group and
`ansible_lxd_remote=local`. This tells Ansible to manage LXD via the local socket
rather than over SSH/Lima.

---

## Starting the environment

Run from `scripts/env-lxd/`:

```bash
./start-env.sh --custom-inventory=config/custom/my-inventory.txt
```

`start-env.sh` automatically calls `scripts/setup-linux-net.sh apply` after the
Ansible run to configure `systemd-resolved` so the host resolves `*.lxd` names
(for example `xrd-cs.lxd`) via LXD's dnsmasq on `lxdbr0`. This requires `sudo`
for the `resolvectl` calls; you will be prompted once.

If your distro does not use `systemd-resolved` (no `resolvectl`), the script
prints the manual fallback and continues. In that case add the LXD DNS server
to your resolver manually:

```bash
# Example for distros using /etc/resolv.conf directly:
echo "nameserver 10.10.10.1" | sudo tee -a /etc/resolv.conf
```

---

## Common flags

```
./start-env.sh --custom-inventory=<path> [options]
  --skip-compile              Skip compilation (packages already built)
  --skip-build                Skip compile + packaging
  --skip-init                 Skip hurl initialisation
  --skip-host-networking      Skip resolvectl setup (no sudo prompt)
  --recreate                  Destroy and recreate containers
  --bust-cache                Delete cached LXD images and refill
  --snapshot-empty-containers Snapshot containers before init (fast reset later)
```

---

## Stopping and cleaning up

```bash
# Stop + clean up host DNS routing:
./stop-env.sh

# Destroy all xrd-* containers:
./scripts/delete-env.sh

# Revert DNS routing only:
./scripts/setup-linux-net.sh cleanup
```

---

## Common URLs after a successful start

- Central Server admin UI: <https://xrd-cs.lxd:4000/>
- Security Server admin UI: <https://xrd-ss0.lxd:4000/>, <https://xrd-ss1.lxd:4000/>
- Test CA web: <http://xrd-ca.lxd:8888/testca/>
- Jaeger traces: <http://xrd-jaeger.lxd/>

---

## Troubleshooting

**`lxc` commands fail with "permission denied"**: your user is not in the `lxd` group yet.
Run `newgrp lxd` or re-login.

**`*.lxd` names do not resolve**: check `scripts/setup-linux-net.sh status`.
If `resolvectl` is not available, add the DNS server manually (see above).

**WSL2 — LXD bridge not present after `lxd init`**: WSL2 does not load kernel modules
automatically. Try `sudo modprobe openvswitch` or restart the WSL2 instance
(`wsl --shutdown` from PowerShell, then re-open).

**Ansible `ansible_connection=lxd` errors**: confirm the `lxd` connection plugin is
available (`ansible-doc -t connection lxd`). Install `community.general` if missing:
`ansible-galaxy collection install community.general`.
