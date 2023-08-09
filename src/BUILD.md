# Building X-Road

Running the X-Road software requires Linux (Ubuntu or RHEL). As a development environment, only Ubuntu (>=20.04, 22.04 recommended) is currently supported. It should be possible to use some other Linux distribution for development, but the instructions and helper scripts assume Ubuntu. Alternatively the software can be built entirely inside docker containers (see below) making the build host distribution agnostic but also a bit slower. If you are using some other operating system (e.g. Windows or macOS), the easiest option is to first install Ubuntu into a virtual machine.

**Tools**

*Required for deb/rpm packaging and/or building in Docker*
* Docker

*Required for building natively (without Docker)*
* OpenJDK / JDK version 11
* Gradle
* GCC
* make

*Recommended for development environment*
* Docker (for deb/rpm packaging)
* LXD (https://linuxcontainers.org/lxd/)
  * for setting up a local X-Road instance
* Ansible
  * for automating the X-Road instance installation

The development environment should have at least 8GB of memory and 20GB of free disk space (applies to a virtual machine as well), especially if you set up a local X-Road instance.

## Dependency installation and instructions for building in Docker

* Install Docker

* Build the software and installation packages:

    `./build_packages.sh -d`

    Note that the `build_package.sh` script's behaviour is controlled by its first command line argument.
    All subsequent arguments will simply be passed to whatever build script it calls to perform the actual build.

## Dependency installation and instructions for building natively (without Docker)

* Requires Ubuntu >=20.04, 22.04 is recommended.

* Execute the following command once to install the required dependencies on a clean building host. The script is supposed to be run as the user who will build the source. The script will ask for user password (using sudo) for installing some new packages as well.

    `./prepare_buildhost.sh`

    *Note*. On Ubuntu 20.04 or newer the script installs Docker (docker.io) for building .deb and .rpm packages. If you want to use Docker CE, install Docker manually (<https://docs.docker.com/install/linux/docker-ce/ubuntu/>) before running this script.

* Build the software and installation packages:

    `./build_packages.sh`

    If Docker is installed, the script will build the .deb and .rpm packages in a Docker container. If Docker is not installed, only .deb packages will be built.

Once you have successfully built the software, please see [ansible/README.md](../ansible/README.md) for local installation instructions.
