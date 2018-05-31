# Guide for setting up a build environment and a local X-Road instance using Vagrant

These steps describe how to set up a development environment in a Virtualbox virtual machine. Note that if you are using Ubuntu >= 14.04, using a virtual machine is not necessary.

## 1. Setup virtualbox image
1. Install [Vagrant](https://www.vagrantup.com/docs/installation/) and [VirtualBox](https://www.virtualbox.org/wiki/Downloads) (tested with VirtualBox 5.1 and 5.2).
2. Run ```vagrant up``` in the `src` folder of the project

## 2. Build X-Road
SSH into the running virtual machine:
```
vagrant ssh
```
One-time setup:
```
git clone https://github.com/ria-ee/X-Road.git
cd X-Road/src
./prepare_buildhost.sh
./update_ruby_dependencies.sh
```
Build the software and create deb/rpm packages:
```
./build_packages.sh
```

On the first run the build will take some time. 

## 3. Setup LXD containers

Please see [ansible/README.md](../ansible/README.md) for additional details.

SSH into the running virtual machine:
```
vagrant ssh
```
Set up LXD containers:
```
cd /home/vagrant/X-Road/ansible
ansible-playbook -i hosts/lxd_hosts.txt xroad_dev.yml
```

## 4. Access X-Road from host
1. On the _host_ machine, add the following route:
  * Linux: `sudo ip route add 10.122.150.0/24 via 10.122.151.3`
  * OSX: `sudo route add 10.122.150.0/24 10.122.151.3`
  * Windows: `route ADD 10.122.150.0 MASK 255.255.255.0 10.122.151.3`
2. List addresses
  * `vagrant ssh -c "lxc list"`
3. You can access thoses addresses from host browsers.
  * CS/SS1/SS2 address is: `https://{ip-address}:4000`
  * Admin username and password for CS/SS1/SS2 
    * User: xrd
    * Password: secret

## 5. Troubleshoot

If you get `Stderr: VBoxManage: error: Could not find a controller named 'SCSI'` error on host machine, find out what is your VirtualBox Storage controller type and fix it in the Vagrantfile:

`vboxmanage showvminfo xroad_dev | grep "Storage Controller Name (1)"`

Change line 27 of Vagrantfile to match the controller name (`'--storagectl', '{Storage-Controller-Name}' )
