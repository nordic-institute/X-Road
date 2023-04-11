# Central Server Docker Image

***The Central Server images are strictly meant for testing and development purposes. Do not use it in production environment!***

The Docker image (`niis/xroad-central-server`) is published on [Docker Hub](https://hub.docker.com/r/niis/xroad-central-server/).

The Central Server Docker image contains vanilla X-Road Central Server version 6.20.0 or later.
All services, including TEST-CA, TSA, OCSP and PostgreSQL database, are installed into the same container and run using supervisord.

The installed Central Server is in uninitialized state.

Admin UI credentials: `xrd`/`secret`

## Building the Central Server image
Run `init_context.sh` script that will collect the necessary files for building the image to `build` folder. After that you can create the image inside the newly created `build` folder.

```shell
cd Docker/centralserver/
./init_context.sh
cd build/
docker build --build-arg DIST=jammy-current -t centralserver -f ../Dockerfile .
```

Alternatively, it's possible to use the image (`niis/xroad-central-server`) available on [Docker Hub](https://hub.docker.com/r/niis/xroad-central-server/).

## Running

Publish the container ports (`4000`, `80` and `9998`) to localhost (loopback address).

Running a locally built image:
```shell
docker run -p 4000:4000 -p 4001:80 -p 4002:9998 --name cs centralserver
```

Running an image available on [Docker Hub](https://hub.docker.com/r/niis/xroad-central-server/):
```shell
docker run -p 4000:4000 -p 4001:80 -p 4002:9998 --name cs niis/xroad-central-server:bionic-7.1.0
```

## Running multiple dockerized X-Road (Security/Central) Servers
If you are running multiple (more than one) containers and map container ports to localhost, it is recommended that you use a separate loopback address for each container and create a X-Road specific network so that containers can communicate.
Accessing admin-ui of a server from the same domain will break session on other servers. You can get over this by setting multiple mappings to localhost in hosts-file.

```shell
# Create a custom network for x-road containers
docker network create -d bridge x-road-network

# Create more than one Central Server containers and (optionally) assign them a network-alias for easier reference
docker run -p 4000:4000 -p 4001:80 -p 4002:9998 --network x-road-network --name cs1 niis/xroad-central-server
docker run -p 4100:4000 -p 4101:80 -p 4102:9998 --network x-road-network --name cs2 niis/xroad-central-server
```

## Initializing vanilla Central Server
After creating a vanilla Central Server, you need to create certificates. You might also find it more convenient to copy the certificates to host machine with the commands below.

```shell
# Create certificates
docker exec -it cs su ca -c 'cd /home/ca/CA && ./init.sh'

# Copy the certificates to host machine
docker cp cs:/home/ca/CA/certs/ca.cert.pem ~/niis/
docker cp cs:/home/ca/CA/certs/ocsp.cert.pem ~/niis/
docker cp cs:/home/ca/CA/certs/tsa.cert.pem ~/niis/
```

### Signing certificates
There is a simple html-form you can use to sign certificates. To access the form from the host machine you must map port `9998` from Central Server container to local port (above examples include this step).

Note that if you use this form to sign certs you will have to restart ocsp-service.

```shell
docker exec -it cs supervisorctl restart ocsp
```

You can also sign the certs manually with scripts in `/home/ca/CA/`.

### OCSP
Inside the container nginx maps the running OCSP to port `8888` so the OCSP responders can be set to `http://[CONTAINER-IP]:8888`.

`[CONTAINER-IP]` can be replaced with the name of the container if the container is added to X-Road network described above.

### TSA
Inside the container nginx maps the running TSA to port `8899` so the timestamping service url can be set to `http://[CONTAINER-IP]:8899`.

`[CONTAINER-IP]` can be replaced with the name of the container if the container is added to X-Road network described above.

### Autologin
The `xroad-autologin` add-on is installed, but there is no default PIN set, so the following error at startup is normal:
```text
... INFO exited: xroad-autologin (exit status 0; not expected)
... INFO gave up: xroad-autologin entered FATAL state, too many start retries too quickly
```
One can create the autologin file by hand after initializing the Central Server:

```shell
$ docker exec cs su -c 'echo 1234 >/etc/xroad/autologin' xroad
$ docker exec cs supervisorctl start xroad-autologin
```
