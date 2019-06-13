# Central server Docker image

***The central server images are strictly meant for testing and development purposes. Do not use it in production environment!***

You can find the docker image in docker hub (https://hub.docker.com/r/niis/xroad-central-server/)

Public central server docker image contains vanilla X-Road central server version 6.20.0 or later.
All services, including TEST-CA, TSA, OCSP and PostgreSQL database, are installed into the same container and run using supervisord.

The installed central server is in uninitialized state.

Admin UI credentials: xrd/secret

## Building centralserver image
Run init_context.sh -script that will collect the necessary files for building the image to build -folder. After that you can create the image inside the newly created build-folder

```shell

# In build-folder
docker build --build-arg DIST=bionic-current -t centralserver -f ../Dockerfile .
```

## Running
```
# Publish the container ports to localhost (loopback address).
docker run -p 4000:4000 -p 4001:80 -p 4002:9998 --name cs niis/xroad-central-server

# Running exact version instead of the default latest version
docker run -p 4000:4000 -p 4001:80 -p 4002:9998 --name cs niis/xroad-central-server:bionic-6.21.0
```

## Running multiple dockerized x-road (security/central) servers
If you are running multiple (more than one) containers and map container ports to localhost, it is recommended that you use a separate loopback address for each container and create a x-road spesific network so that containers can communicate.
Accessing admin-ui of a server from the same domain will break session on other servers. You can get over this by setting multiple mappings to localhost in hosts-file.

```
# Create a custom network for x-road containers
docker network create -d bridge x-road-network

# Create more than one central server containers and (optionally) assign them a network-alias for easier reference
docker run -p 4000:4000 -p 4001:80 -p 4002:9998 --network x-road-network --name cs1 niis/xroad-central-server
docker run -p 4100:4000 -p 4101:80 -p 4102:9998 --network x-road-network --name cs2 niis/xroad-central-server
```

## Initializing vanilla central server
After creating vanilla central server you need create certificates. You might also find it more convenient to copy the certificates to host machine with the commands below.

```
# Create certificates
docker exec -it cs su ca -c 'cd /home/ca/CA && ./init.sh'

# Copy the certificates to host machine
docker cp cs:/home/ca/CA/certs/ca.cert.pem ~/niis/
docker cp cs:/home/ca/CA/certs/ocsp.cert.pem ~/niis/
docker cp cs:/home/ca/CA/certs/tsa.cert.pem ~/niis/
```

### Signing certificates
There is a simple html-form you can use to sign certificates. To access the form from the host machine you must map port 9998 from central server container to local port (above examples include this step).

Note that if you use this form to sign certs you will have to restart ocsp-service.

```shell
docker exec -it cs supervisorctl restart ocsp
```

You can also sign the certs manually with scripts in /home/ca/CA/

### OCSP
Inside the container nginx maps the running OCSP to port 8888 so the OCSP responders can be set to http://[CONTAINER-IP]:8888

[CONTAINER-IP] can be replaced with the name of the container if the container is added to x-road-network described above

### TSA
Inside the container nginx maps the running TSA to port 8899 so the timestamping service url can be set to http://[CONTAINER-IP]:8899

[CONTAINER-IP] can be replaced with the name of the container if the container is added to x-road-network described above

### Autologin
xroad-autologin is installed, but there is no default PIN set, so the following error at startup is normal:
```
... INFO exited: xroad-autologin (exit status 0; not expected)
... INFO gave up: xroad-autologin entered FATAL state, too many start retries too quickly
```
One can create the autologin file by hand after initializing the central server:

```
$ docker exec cs su -c 'echo 1234 >/etc/xroad/autologin' xroad
$ docker exec cs supervisorctl start xroad-autologin
```
