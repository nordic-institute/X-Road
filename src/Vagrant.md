# Guide to use build environment and local X-Road instance
This guide is for development tool set. User can build and test changes in local environment. Environment doesn't ship with gui.

## 1. Setup virtualbox image
1. Install [Vagrant](https://www.vagrantup.com/docs/installation/) and [Virtualbox](https://www.virtualbox.org/manual/ch02.html) (==v5.1). Host machine will need ~20GB free Storage and 4GB ram for virtual machine.
2. Run ```vagrant up``` at source folder of project
3. Get X-Road repository
    - ```vagrant ssh```
        - ```git clone https://github.com/ria-ee/X-Road.git```
        - ```cd X-Road/src```
        - ```./update_ruby_dependencies.sh```

## 2. Build X-Road
- ```vagrant ssh```
    - ```cd X-Road/src```
    - ```./build_packages.sh```

## 3. Setup ldx containers
- ```vagrant ssh```
    - ```cd ../ansible```
    - ```ansible-playbook  -i hosts/lxd_hosts.txt xroad_dev.yml```

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
- If you get ```Stderr: VBoxManage: error: Could not find a controller named 'SCSI'``` error. Theses commants happens in host machine.

    Solution is find out what is your VirtualBox Storage controller type or uptadeting virtualbox
    - ```vboxmanage showvminfo {virtualboxname}```
    - Find line with ```Storage Controller Name (1):``` it will follow name of Storage contoller, you need to change line 27 from Vagrant file to match that.
        ```'--storagectl', '{Storage-Contoler-Name}',```
