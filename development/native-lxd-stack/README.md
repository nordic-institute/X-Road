## LXD based development environment.

This uses native, locally built packages to deploy LXD based environment. LXC is used to manage containers.

**Remember: most scripts are configurable, refer to their source or --help for more details.**

### Prerequisites

- LXD host (linux, macOS via Lima, WSL2)
- LXC installed on the host

### Usage within MacOS

```bash

# Setup MacOS host.
./scripts/setup-mac.sh
# Compile code -> create packages -> deploy.
./start-env.sh
```

### Usage within Linux

Since Linux doesn't require Lima, it should suffice to use local with lxd servers:
```
[lxd_servers]
localhost ansible_connection=local
...
[all:vars]
ansible_lxd_remote=local
```
and then:
1. Create new inventory in `config/custom`
3. Run `./start-env.sh --custom-inventory=config/custom/my-inventory.txt`

### Usage within other hosts

It is assumed that LXD host is available on `127.0.0.1:28443`

Default hosts assume presence of Lima, but you can specify your own custom inventory based on it.

1. Create new inventory in `config/custom`
2. Specify inventory in `start-env.sh` script.

```bash

#Setup LXC
./scripts/setup-lxc.sh
# Compile code -> create packages -> deploy.
./start-env.sh --custom-inventory=config/custom/my-inventory.txt
```