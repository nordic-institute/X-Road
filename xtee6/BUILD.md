# X-Road build instructions

Simple dependency installation and building instructions for Ubuntu 14.04:

* Execute the following command once to install the system dependencies on a clean building host. The script is supposed to be run as the user who will build the source. The script will ask for user password (using sudo) for installing some new packages as well.

    `./prepare_buildhost.sh`

* Update/install software dependencies. It's needed only on first build or in case the changelog states so.

    `./update_ruby_dependencies.sh`

* Build the software and Ubuntu packages:

    `./build_packages.sh`
