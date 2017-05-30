# X-Road installation instructions

## 1. Install Ansible

Install Ansible by following instructions at http://docs.ansible.com/ansible/intro_installation.html

## 2. Configuration options

The servers to be installed are written in the hosts-file. In the provided example command they are in `hosts/example_xroad_hosts.txt`. Host names in the file must be correct fully qualified host names because they are used in X-Road certificate generation and setting the hostname of the servers when installing. Do not use IP addresses.

The admin username and password for the servers can be configured. In the provided example the configuration file is in `group_vars/example.yml`. Notice that it is referenced from the hosts file.

## 3. Install X-Road using Ansible

`ansible-playbook -i hosts/example_xroad_hosts.txt palveluvayla_init.yml`
