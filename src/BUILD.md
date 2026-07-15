# Building X-Road

Running the X-Road software requires Linux (Ubuntu or RHEL). As a development environment, only Ubuntu (>=20.04, 22.04 recommended) is currently supported. It should be possible to use some other Linux distribution for development, but the instructions and helper scripts assume Ubuntu. Alternatively the software can be built entirely inside docker containers (see below) making the build host distribution agnostic but also a bit slower. If you are using some other operating system (e.g. Windows or macOS), the easiest option is to first install Ubuntu into a virtual machine.

**Tools**

*Required for deb/rpm packaging and/or building in Docker*
* Docker

*Required for building natively (without Docker)*
* OpenJDK / JDK version 21
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

## Building in Docker

* Install Docker.

* Build builder images for each target distribution, then build the packages:

    ```
    deployment/native-packages/docker/prepare-builder-image.sh deb-noble
    deployment/native-packages/docker/prepare-builder-image.sh deb-resolute
    deployment/native-packages/docker/prepare-builder-image.sh rpm-el9
    deployment/native-packages/docker/prepare-builder-image.sh rpm-el10
    ./build_packages.sh --package-only
    ```

    Or build packages for specific releases only:

    ```
    ./build_packages.sh --package-only -r noble -r rpm-el9
    ```

## Building natively (without Docker)

* Requires Ubuntu >=20.04, 22.04 is recommended.

* Install the required build dependencies manually: OpenJDK 21, Gradle, GCC, make.

* Build the software and installation packages:

    `./build_packages.sh`

    If Docker is installed, the script will also build the .deb and .rpm packages in Docker containers. If Docker is not installed, only .deb packages for the current distribution will be built.

Once you have successfully built the software, please see [development/ansible/README.md](../development/ansible/README.md) for local installation instructions.
