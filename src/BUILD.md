# Building X-Road

Running the X-Road software requires Linux (Ubuntu or RHEL). As a development environment,  only Ubuntu (>=16.04, 18.04 recommended) is currently supported. It should be possible to use some other Linux distribution for development, but the instructions and helper scripts assume Ubuntu. If you are using some other operating system (e.g. Windows or macOS), the easiest option is to first install Ubuntu 18.04 into a virtual machine.

**Tools**

*Required for building*
* OpenJDK / JDK version 8
* Gradle
* JRuby and rvm (ruby version manager)
* GCC

*Recommended for development environment*
* Docker (for deb/rpm packaging)
* LXD (https://linuxcontainers.org/lxd/)
  * for setting up a local X-Road instance
* Ansible
  * for automating the X-Road instance installation

The development environment should have at least 8GB of memory and 20GB of free disk space (applies to a virtual machine as well), especially if you set up a local X-Road instance.
