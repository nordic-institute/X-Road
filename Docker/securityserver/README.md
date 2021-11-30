# Security server Docker image

Note! This security server image is meant local testing and development. See [X-Road Security Server Sidecar](https://github.com/nordic-institute/X-Road-Security-Server-Sidecar) for production use.

All services, including the serverconf and messagelog PostgreSQL databases, are installed into the same container and run using supervisord.
The installed security server is in uninitialized state.

Admin UI credentials: xrd/secret

## Running
```
# Build the image
docker build -t xroad-security-server .

# Publish the container ports (8080 and/or 8443, 4000, and optionally 5500 and 5577) e.g. to localhost (loopback address).
# One can pass the token pin code for autologin using XROAD_TOKEN_PIN environment variable
docker run -p 127.0.0.1:4000:4000 -p 127.0.0.1:8080:8080 --name my-ss -e XROAD_TOKEN_PIN=1234 xroad-security-server
```

## Running multiple dockerized x-road (security/central) servers
If you are running multiple (more than one) containers and map container ports to localhost, it is recommended that you use a separate loopback address for each container and create a x-road spesific network so that containers can communicate.
Accessing admin-ui of a server from the same domain will break session on other servers. You can get over this by setting multiple mappings to localhost in hosts-file.

```shell
# Create a custom network for x-road containers, publish by default to localhost only
docker network create -d bridge --opt com.docker.network.bridge.host_binding_ipv4=127.0.0.1 x-road-network

# Create more than one security server containers and (optionally) assign them a network-alias for easier reference
docker run -p 4001:4000 -p 8081:8080 --network x-road-network --name my-ss1 xroad-security-server
docker run -p 4002:4000 -p 8082:8080 --network x-road-network --name my-ss2 xroad-security-server

```

## Running multiple security servers via docker-compose

docker-compose.yml example file:

```yaml
version: '3.3'

services:
  my-ss-1:
    image: xroad-security-server
    environment:
      - XROAD_TOKEN_PIN="1234"
    ports:
      - "127.0.0.1:4001:4000"
      - "127.0.0.1:8081:8080"
    networks:
      - x-road-network

  my-ss-2:
    image: xroad-security-server
    environment:
      - XROAD_TOKEN_PIN="1234"
    ports:
      - "127.0.0.1:4002:4000"
      - "127.0.0.1:8082:8080"
    networks:
      - x-road-network

networks:
  x-road-network:
    driver: bridge
    driver_opts:
      com.docker.network.bridge.host_binding_ipv4: 127.0.0.1
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
