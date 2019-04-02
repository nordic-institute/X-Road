# Security server Docker image

***The security server images are strictly meant for testing and development purposes. Do not use it in production environment!***

You can find the docker image in docker hub (https://hub.docker.com/r/niis/xroad-security-server/)

Public security server docker image contains vanilla X-Road security server version 6.20.0 or later.
All services, including the serverconf and messagelog PostgreSQL databases, are installed into the same container and run using supervisord.

The installed security server is in uninitialized state.

Admin UI credentials: xrd/secret

## Running
```
# Simple example 
docker run --name my-ss niis/xroad-security-server

# Running exact version instead of the default latest version
docker run --name my-ss niis/xroad-security-server:bionic-6.20.0
```

## Running an ephemeral container, storing state in named volumes (experimental)
```
docker run --name my-ss --rm \
-v my-ss-etc-xroad:/etc/xroad \
-v my-ss-db:/var/lib/postgresql/10/main \
niis/xroad-security-server
```

## Accessing the container

#####Running a single security server (Linux/Windows/MacOS)
```
# Publish the container ports (80 and/or 443, 4000, and optionally 5500 and 5577) e.g. to localhost (loopback address).
docker run -p 4000:4000 -p 4001:80 --name my-ss niis/xroad-security-server
```

##### Direct access
###### Linux

```
# Works on Windows and Linux (can use any address from 127.0.0.0/8).
docker run -p 127.42.1.1:4000:4000 -p 127.42.1.1:80:80 --name my-ss niis/xroad-security-server
```
On a Linux host, it is also possible to access the container(s) directly since the virtual network bridge created by docker can be accessed from the host (`docker inspect my-ss` shows the container IP address).

###### Windows

```
# Works on Windows and Linux (can use any address from 127.0.0.0/8).
docker run -p 127.42.1.1:4000:4000 -p 127.42.1.1:80:80 --name my-ss niis/xroad-security-server
```
On Windows, direct access does not work by default. See https://stackoverflow.com/questions/39154408/connecting-to-containers-ip-address-is-impossible-in-docker-for-windows for a possible solution.

###### MacOS
Preferably use port mappings described above. Directly accessing the container on macOS is possible but currently you have to use for example this tool from a private person (https://github.com/AlmirKadric-Published/docker-tuntap-osx). There is a long-standing feature request for the functionality: https://github.com/docker/for-mac/issues/155

## Running multiple dockerized security servers
If you are running multiple containers and map container ports to localhost, it is recommended that you use a separate loopback address for each container and create a x-road spesific network so that containers can communicate. 
Accessing admin-ui of a server from the same domain will break session on other servers. You can get over this by setting multiple mappings to localhost in hosts-file.

```
# Create a custom network for x-road containers
docker network create -d bridge x-road-network

# Create more than one security server containers and (optionally) assign them a network-alias for easier reference
docker run -p 4000:4000 -p 4001:80 --network x-road-network --network-alias --name my-ss1 niis/xroad-security-server
docker run -p 4100:4000 -p 4101:80 --network x-road-network --network-alias --name my-ss2 niis/xroad-security-server
```

##### 

## Notes
xroad-autologin is installed, but there is no default PIN set, so the following error at startup is normal:
```
... INFO exited: xroad-autologin (exit status 0; not expected)
... INFO gave up: xroad-autologin entered FATAL state, too many start retries too quickly
```
One can create the autologin file by hand after initializing the security server:

```
$ docker exec my-ss su -c 'echo 1234 >/etc/xroad/autologin' xroad
$ docker exec my-ss supervisorctl start xroad-autologin

```
