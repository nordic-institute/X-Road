# Guide to use build environment and local X-Road instance
This guide is for development tool set. User can build and test changes in local environment. Environment doesn't ship with GUI.

## 1. Setup virtualbox image
1. Install [Vagrant](https://www.vagrantup.com/docs/installation/) and [Virtualbox](https://www.virtualbox.org/manual/ch02.html) (==v5.1). Host machine will need ~20GB free Storage and 4GB ram for virtual machine.
2. Run ```vagrant up``` at source folder of project
3. Get X-Road repository
    - ```vagrant ssh```
        - ```git clone https://github.com/ria-ee/X-Road.git```

## 2. Build X-Road
- ```vagrant ssh```
    - ```cd X-Road/src```
    - ```./prepare_buildhost.sh```
    - ```./update_ruby_dependencies.sh```
    - ```./build_packages.sh```

## 3. Setup LXD containers
- ```vagrant ssh```
    - ```cd X-Road/ansible```
    - ```ansible-playbook -i hosts/lxd_hosts.txt xroad_dev.yml```

## 4. Access X-Road from host
1. Create routing
    - Linux
        * ```sudo ip route add 10.122.150.0/24 via 10.122.151.3```
    - OSX
        * ```sudo route add 10.122.150.0/24 10.122.151.3```
    - Windows
        * ```route ADD 10.122.150.0 MASK 255.255.255.0 10.122.151.3```
2. List addresses
    - ```vagrant ssh```
        - ```lxc list```
3. You can access thoses address from host browsers.
    - CS/SS1/SS2 address is: ```https://{ip-address}:4000```
    - Username and password for CS/SS1/SS2
        ```
         u: xrd
         p: secret
        ```

## 5. Troubleshoot
- If you get ```Stderr: VBoxManage: error: Could not find a controller named 'SCSI'``` error on host machine.

    Solution is to find out what is your VirtualBox Storage controller type and set it correctly. Alternatively you can try updating Virtualbox.
    - ```vboxmanage showvminfo {virtualboxname}```
    - Find line with ```Storage Controller Name (1):``` followed with name of Storage controller. Change line 27 of Vagrantfile to match that.
        ```'--storagectl', '{Storage-Controller-Name}',```
