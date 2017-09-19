# X-Road build instructions

Simple dependency installation and building instructions for Ubuntu >=14.04:

* Execute the following command once to install the system dependencies on a clean building host. The script is supposed to be run as the user who will build the source. The script will ask for user password (using sudo) for installing some new packages as well.

    `./prepare_buildhost.sh`

    Note that on Ubuntu 16.04 or newer the script installs Docker for building .deb and .rpm packages. On Ubuntu 14.04, please install Docker manually (<https://docs.docker.com/engine/installation/linux/ubuntulinux/>) if you want to build .rpm packages.

* Update/install ruby dependencies. It's needed only on first build or in case the changelog states so.

    `./update_ruby_dependencies.sh`

* Build the software and installation packages:

    `./build_packages.sh`
    
    If Docker is installed, the script will build the .deb and .rpm packages in a Docker container. If Docker is not installed, only .deb packages will be built.