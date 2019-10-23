# Security server Docker image

***The security server images are strictly meant for testing and development purposes. Do not use it in production environment!***

You can find the docker image in docker hub (https://hub.docker.com/r/niis/xroad-security-server/)

Public security server docker image contains vanilla X-Road security server version 6.20.0 or later.
All services, including the serverconf and messagelog PostgreSQL databases, are installed into the same container and run using supervisord.

The installed security server is in uninitialized state.

Admin UI credentials: xrd/secret

## Running
```
# Publish the container ports (80 and/or 443, 4000, and optionally 5500 and 5577) e.g. to localhost (loopback address).
# One can pass the token pin code for autologin using XROAD_TOKEN_PIN environment variable
docker run -p 4000:4000 -p 4001:80 --name my-ss -e XROAD_TOKEN_PIN=1234 niis/xroad-security-server

# Running exact version instead of the default latest version
docker run -p 4000:4000 -p 4001:80 --name my-ss niis/xroad-security-server:bionic-6.20.0
```

## Running multiple dockerized x-road (security/central) servers
If you are running multiple (more than one) containers and map container ports to localhost, it is recommended that you use a separate loopback address for each container and create a x-road spesific network so that containers can communicate.
Accessing admin-ui of a server from the same domain will break session on other servers. You can get over this by setting multiple mappings to localhost in hosts-file.

```shell
# Create a custom network for x-road containers
docker network create -d bridge x-road-network

# Create more than one security server containers and (optionally) assign them a network-alias for easier reference
docker run -p 4000:4000 -p 4001:80 --network x-road-network --name my-ss1 niis/xroad-security-server
docker run -p 4100:4000 -p 4101:80 --network x-road-network --name my-ss2 niis/xroad-security-server

```

## Running multiple security servers via docker-compose

docker-compose.yml example file:

```yaml
version: '3.3'

services:
  my-ss-1:
    image: niis/xroad-security-server:latest
    environment:
      - XROAD_TOKEN_PIN="1234"
    ports:
      - "4000:4000"
      - "4001:80"
    networks:
      - x-road-network

  my-ss-2:
    image: niis/xroad-security-server:latest
    environment:
      - XROAD_TOKEN_PIN="1234"
    ports:
      - "4100:4000"
      - "4101:80"
    networks:
      - x-road-network

networks:
  x-road-network:
    driver: bridge
```

## Notes

xroad-autologin is installed, but there is no default PIN set, so the following error at startup is normal:

```text
... INFO exited: xroad-autologin (exit status 0; not expected)
... INFO gave up: xroad-autologin entered FATAL state, too many start retries too quickly
```

One can create the autologin file by hand after initializing the security server:

```shell
docker exec my-ss su -c 'echo 1234 >/etc/xroad/autologin' xroad
docker exec my-ss supervisorctl start xroad-autologin
```
