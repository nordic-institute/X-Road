# Using this Docker Compose environment

**NB!** The compose environment is meant only for testing X-Road in a development setup, it should never be used in a
production environment.

This document expects that you have a working Docker setup with Docker Compose installed on your system.
Setup was tested with Docker version `24.x` and Docker Compose version `2.24.x`.
There is no guarantees that this environment will work with older versions.

The environment is set up by default to be used with the `xrddev-*` images available from the
[main X-Road repository](https://github.com/orgs/nordic-institute/packages?repo_name=X-Road).

The Compose environment also contains a Hurl container and scripts to initialize an environment. Stack consists of:

* **CS**: Central Server
* **SS0** One management Security Server, which also acts as the producer Security Server for the example adapter under the
  `DEV:COM:1234:TestService` subsystem. Permissions are given to the `DEV:COM:4321:TestClient` subsystem to access all
  requests under it.
* **SS1** One consumer Security Server, which has the `DEV:COM:4321:TestClient` subsystem registered to it.
* **ISSOAP** The `example-adapter` container. More information regarding it is available in
  [its own repository](https://github.com/nordic-institute/xrd4j/tree/develop/example-adapter).
* **ISOPENAPI** The `example-restapi` container. More information regarding it is available in
  [its own repository](https://github.com/nordic-institute/x-road-example-restapi).
* **ISREST** Wiremock container with a simple predefined rest endpoint.
* **TESTCA** CA authority for dev env.

The default compose file does not forward any ports to the host system, but dev profile does provide extensive list of ports.
See: [compose.dev.yaml](compose.dev.yaml) for details

Please note that the containers do not have any persistent volume mappings, so once they are removed, all data is also
lost.

## Settings up environment from remote images

### Prerequisites:

* Optionally set the `XROAD_HOME` environment variable in your shell profile. This variable should point to the root
  directory of the X-Road source code. This is needed for the scripts to find the correct files if the shell script is
  executed from a different working directory.

### Creating the environment

To start the containers, use provided script:

```bash
./local-dev-run.sh --initialize
```

Initialising the environment will take a few minutes, and there will be several points where it will get HTTP errors
and keep retrying. This is normal and is due to the time it takes for the global configuration updates to happen and be
distributed to the Security Servers.

The command should finish with last hurl request being successful.

NOTE: Refer to provided bash files for additional customization options.

## Setting up an environment based on your own local code

You can also build the X-Road source code and use the resulting packages to deploy the containers. Use the following commands:

```bash
./local-dev-prepare.sh
./local-dev-run.sh --initialize --local
```

This step expects that you are able to build and package the X-Road source code.

This script will do the following:

* Build the source code with Gradle
  * Type `./local-dev-prepare.sh -h` for complete list of usage arguments. For example:
    * `--skip-gradle-build` to skip gradle build
    * `--skip-tests` to skip tests
    * `--parallel` to run gradle build in parallel
    * `-r release-name` for a specific release only
* Build Ubuntu Jammy packages in Docker
* Copy the resulting Debian packages to their correct locations
* Build the `centralserver`, `securityserver` and `testca` Docker images
* Start the Docker Compose environment
