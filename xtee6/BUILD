
Simple depenency installing and building instructions on Ubuntu 14.04:

0. Execute following command once for installing system depenencies on clean
   building host. Script is ment to be run as user who will build the source.
   Script will ask for user password (using sudo) for installing some new
   packages as well.

   ./prepare_buildhost.sh


1. Update/install software depenencies:

   ./update_ruby_dependencies.sh

   Its needed only on first build or changelog states so.

2. Build software and Ubuntu packages in SOURCE_DIR:

   ./build_packages.sh


PS! Building and installing packages in the same computer causes shared
memory access rights errors in case running the building process by a user
other then 'xroad'. To overcame it reboot the computer or use the system
utilities ipcs and ipcrm to find and delete corresponding shared memory
segment (key 0xa1010002).

