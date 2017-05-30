# X-Road build instructions

Simple dependency installation and building instructions for Ubuntu 14.04:

* Execute the following command once to install the system dependencies on a clean building host. The script is supposed to be run as the user who will build the source. The script will ask for user password (using sudo) for installing some new packages as well.

    `./prepare_buildhost.sh`

* Update/install software dependencies. It's needed only on first build or in case the changelog states so.

    `./update_ruby_dependencies.sh`

* Build the software and Ubuntu packages:

    `./build_packages.sh`
    
## Building Ubuntu 14.04 packages using Docker

* Install and configure Docker >= 1.8.0
* Make sure that `prepare_buildhosts.sh`and `update_ruby_dependencies.sh` have been run as described above
* Compile the code

	`./gradlew --stacktrace buildAll runProxyTest`
    
* Build Docker image

	`docker build -t docker-debbuild $(pwd)/packages/docker-debbuild`
    
* Run the package build using docker

	`docker run -v $(pwd)/..:/workspace -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro docker-debbuild`
