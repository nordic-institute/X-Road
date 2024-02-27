# Using this Docker Compose environment

**NB!** The compose environment is meant only for testing X-Road in a development setup, it should never be used in a
production environment.

This document expects that you have a working Docker setup with Docker Compose installed on your system.

The environment is set up by default to be used with the `xrddev-*` images available from the
[main X-Road repository](https://github.com/orgs/nordic-institute/packages?repo_name=X-Road).

The Compose environment also contains a Hurl container and scripts to initialize an environment with the following
settings:

* One Central Server
* One management Security Server, which also acts as the producer Security Server for the example adapter under the
  `DEV:COM:1234:TestService` subsystem. Permissions are given to the `DEV:COM:4321:TestClient` subsystem to access all
  requests under it.
* One consumer Security Server, which has the `DEV:COM:4321:TestClient` subsystem registered to it.
* The `example-adapter` container. More information regarding it is available in
  [its own repository](https://github.com/nordic-institute/xrd4j/tree/develop/example-adapter).

The compose file has the following port mappings for the UI-s:

* 4001 - Central Server
* 4002 - Management Security Server
* 4003 - Consumer Security Server
* 8080 - Consumer Security Server client port
* 8888 - TestCA

Please note that the containers do not have any persistent volume mappings, so once they are removed, all data is also
lost.

## Starting and initialising the environment

To start the containers, simply run the following command under the directory `.github/dev-containers/devenv`:

```bash
docker compose up -d
```

After all of the containers have started, you can initialise the environment with the following command in the same
directory:

```bash
docker compose run hurl --insecure --variables-file /hurl-src/vars.env --file-root /hurl-files /hurl-src/setup.hurl --very-verbose --retry 12 --retry-interval 10000
```

Initialising the environment will take a few minutes, and there will be several points where it will get HTTP errors
and keep retrying. This is normal and is due to the time it takes for the global configuration updates to happen and be
distributed to the Security Servers.

The command should finish with a successful reply from the `example-adapter`'s `getRandom` service if everything went
as planned.

## Setting up an environment based on your own local code

This step expects that you are able to build and package the X-Road source code. In that case the complest way to
deploy containers based on your own code is to use the script and environmental overrides for the compose file.

Assuming that you are starting from the root of this repository, run the following commands:

```bash
cd .github/dev-containers
./build-local.sh
```

This script will do the following:

* Build the source code with Gradle
* Build Ubuntu Jammy packages in Docker
* Copy the resulting Debian packages to their correct locations
* Build the `centralserver`, `securityserver` and `testca` Docker images

After that, navigate to the `.github/dev-containers/devenv` directory and create a `.env.override` file with the
following content:

```ini
CS_IMG=centralserver
SS_IMG=securityserver
CA_IMG=testca
```

Then in the same directory, run the following command:

```bash
docker compose --env-file .env.override up -d
```

After that you can use the `hurl` command as shown in the previous example.
