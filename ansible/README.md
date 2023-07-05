# X-Road installation instructions

Please note that this Ansible collection and the corresponding documentation is for creating a development-use X-Road system only.

## 1. Install Ansible

The playbooks require Ansible 7.x (Ansible Core 2.14) or later. Using the latest stable version of Ansible is strongly recommended.

Install Ansible by following instructions at <https://docs.ansible.com/ansible/latest/installation_guide/index.html>.

## 2. Configuration options

#### Inventory

The hosts for X-Road servers must be listed in the used Ansible inventory (host-file).
It is recommended to create your own host file in order to best facilitate further configuration of your installations.

The `hosts` directory contains examples of inventory host files that are used in the example commands provided in
[section 3](#3-install-x-road-using-ansible) and
[section 4](#4-development-using-ansible-playbooks) (`hosts/example_xroad_hosts.txt` and `hosts/lxd_hosts.txt`). These files also contain a short description of the purpose of each group.

Host names in the file must be correct fully qualified host names because they are used
in X-Road certificate generation and setting the hostname of the servers when installing. Do not use IP addresses.

You determine which servers are initialized by filling in the groups
`cs_servers`, `ss_servers`, `cp_servers` and `ca_servers`. If you have no use for a specific server group,
you can leave that group empty.

**Note:** Study the structure of the example host files carefully and model the group hierarchies that you wish to implement in your own
inventory files. For example, the group `[centos_ss]` for CentOS-based security server LXD-containers is a child group to the security server group `[ss_servers]` and can be omitted entirely if you have no use for CentOS containers or are using the ee-variant.

#### Variant

When installing security servers, the Ansible playbooks use the configuration variable `variant`
to select one of the available security server variants for installation. If no additional configurations have been made, the playbooks will install the `vanilla` variant. The three currently supported variants are:
- `vanilla` - the basic non-country-specific version of X-Road
- `ee` - the Estonian country variant, no RHEL variant package available
- `fi` - the Finnish country variant

Country variants provide country-specific configuration options and dependencies in order to suit the X-Road instances and policies of their host countries.
The vanilla variant provides an operational X-Road installation without any country-specific configurations. Vanilla configurations can be considered the default X-Road configurations.

The recommended place for defining a different variant is the inventory file. For example, the definition for the Finnish variant:
```
[ss_servers:vars]
variant=fi
```

While it is possible to define different variants for different security servers, it is worth noting that upgrading from one variant to another is not supported.

#### Package repository

Playbook `xroad_init.yml` uses package repositories for X-Road installations.
The default repository configurations are:

* for Ubuntu 22.04 DEB-packages `deb https://artifactory.niis.org/xroad-release-deb jammy-current main`
* for RHEL 8 packages `https://artifactory.niis.org/xroad-release-rpm/rhel/8/current`.

The used repository can be configured in `vars_files/remote_repo.yml`. The file contains repository and key variables for RHEL and Ubuntu.

#### Remote database

It is possible to configure Central Server or Security Server to use remote database. To do this with Ansible one needs to edit `vars_files/cs_database` and/or `vars_files/ss_database` property values.

- `database_host` - URL or the database server including the port e.g. `127.0.0.1:5432`. When using remote database also set `database_admin_password`. When using local database leave it empty.
- `database_admin_password` - Password of the `postgres` user. When using remote database, this value needs to be set. Otherwise leave it empty.
- `mask_local_postgresql` - When using remote database, it is usually feasible to mask the local PostgreSQL database so it won't run in vein. However, in some edge cases this is not necessary and this variable can be set to false.

Other properties in `vars_files/cs_database` and `vars_files/ss_database` determine the usernames and passwords that X-Road uses in connections.

#### Additional variables

Parameters such as the admin username and password or the CA server's distinguished name values
can be configured using files in the `group_vars` directory.
In the example provided in [section 3](#3-install-x-road-using-ansible) the configuration file matching the super group `example` is `group_vars/example.yml`.

The inventory file `example_xroad_hosts.txt` defines a host group

```
[example:children]
 cs_servers
 ss_servers
 cp_servers
 ca-server
```

containing all other X-Road server host groups in the inventory. Ansible searches for variables in `group_vars/example.yml`
when initializing hosts from `example` group.

Username and password are also defined in the default variables of the `xroad-base` role. This definition will be used
if no group vars or other higher precedence configurations for the same variables have been assigned.

## 3. Install X-Road using Ansible

Before running any playbooks, be sure to configure your inventory file to specify the composition of hosts for the X-Road installation. Also check the [configuration section](#2-configuration-options) for additional configuration options.

The following command installs or updates **all X-Road related packages** to latest versions **from package repositories** for all hosts defined in the inventory file `example_xroad_hosts.txt`:

```
ansible-playbook -i hosts/example_xroad_hosts.txt xroad_init.yml
```

For repository configuration, check the detailed instructions in the [repository configuration section](#package-repository).


## 4. Development using Ansible playbooks

#### Installing X-Road from locally built packages

Compile your own X-Road, build packages and install X-Road from your own packages using the playbook `xroad_dev.yml`.

First make sure that `docker` and [Docker SDK for Python](https://pypi.org/project/docker/) are installed on the compilation machine. Docker is used for building DEB- and RHEL-packages.

```
ansible-playbook -i hosts/example_xroad_hosts.txt xroad_dev.yml
```

This installs or updates **all X-Road related packages to their latest versions** using **locally built X-Road packages** (as long as `compile_servers` group in ansible inventory has the value `localhost`) for hosts defined in the inventory file `example_xroad_hosts.txt`.
Package version names are formed using current git commit timestamp and hash. This means that

* if you only make local changes without performing a git commit, package names and version numbers are
not changed - and executing `xroad_dev.yml` playbook will not update the packages of an existing installation with the same git commit
* if you perform a git commit (of any kind), all local modifications will be packaged and deployed
using the new version number - and all local modifications (whether they were included in the commit or not)
will be deployed

In short, to deploy made changes with package installation, **a git commit must be made**.

#### Partial compilation and deployment

For fast development, you can compile and update modules separately using the ansible playbook `xroad_dev_partial.yml`. For example, if you make a change to a Java or Javascript file under the module `proxy-ui-api`, use the following command to compile the WAR and deploy it to the existing security server installations.

```
ansible-playbook -i hosts/example_xroad_hosts.txt xroad_dev_partial.yml -e selected_modules=proxy-ui-api
```

It is also possible to compile and update several modules (JARs or WARs). The following command compiles and updates JAR-files for modules `common-util`, `signer` and `proxy-ui-api` to the defined existing server installations.

```
ansible-playbook -i hosts/example_xroad_hosts.txt xroad_dev_partial.yml -e selected_modules=common-util,proxy-ui-api,signer
```

This updates the **selected modules (JARs or WARs)** to ones compiled locally.
**No git commits are needed** to see the changes.

The modules are listed under `vars_files` in dicts `common_modules.yml`, `cs_modules.yml`, `cp_modules.yml` and `ss_modules.yml`.

Note that the playbook `xroad_dev_partial.yml` only copies JARs and WARs to the appropriate locations on the servers and can only be used to update made changes to existing server installations. For the full, package-based installation, use `xroad_dev.yml` described in [section 4](#4-development-using-ansible-playbooks).

#### Using LXD-hosts

On Linux it is possible to use the LXD-container hypervisor for fast testing and development.

First, make sure that you have LXD installed and configured as explained on [https://linuxcontainers.org/lxd/getting-started-cli/](https://linuxcontainers.org/lxd/getting-started-cli/). For example, on Ubuntu:

```
sudo apt-get install lxd

# Log in to the new group lxd (or logout/login)
newgrp lxd

# Configure lxd (use bridge network configuration and turn on nat)
sudo lxd init
```

An example of an LXD-specific host file can be found in `hosts/lxd_hosts.txt`. While it is possible to use and modify this file it is recommended to create your own host file to facilitate further configuration.

Some LXD installations may create the Unix-domain socket for connecting to the service in a location different from those searched by ansible. If that is the case in your environment, override the variable `lxd_url` for the server running LXD (group `lxd_servers` in the example inventory file) by creating a yml file in the `group_vars` directory.

The playbook initializes LXD-containers according to the hosts defined in the inventory. The host for the LXD-containers themselves is
defined with the group `lxd_servers`, normally localhost. After inventory configurations, Ansible playbooks can be used to deploy X-Road to the LXD-hosts much like with other inventories.

Install packages to local LXD-containers from the public repositories with:

```
ansible-playbook  -i hosts/lxd_hosts.txt xroad_init.yml
```

Install locally built X-Road packages to LXD-containers with:

```
ansible-playbook  -i hosts/lxd_hosts.txt xroad_dev.yml
```

Update module `proxy-ui-api` to an existing LXD-container X-Road installation with:

```
ansible-playbook  -i hosts/lxd_hosts.txt xroad_dev_partial.yml -e selected_modules=proxy-ui-api
```

#### Controlling the LXD operating system versions

By default `xroad_dev.yml` creates Ubuntu 22.04 and CentOS 8 containers. It is also possible to configure it to create other versions of operating systems. To do this, in `groups_vars/all/vars.yml` set variables `centos_releasever` and `ubuntu_releasever`. Out of the box there is support for CentOS 7 and 8, and Ubuntu 20.04 and 22.04. Other versions may need additional tweaking of the Ansible scripts.

## 5. Test CA, TSA, and OCSP

While not themselves components provided by X-Road, certification and time stamping authorities are crucial to messaging within the system. More information on creating development-use CA, TSA and OCSP services [here.](TESTCA.md)
