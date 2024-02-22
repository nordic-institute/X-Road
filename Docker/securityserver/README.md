# Security Server Docker Image

***This Security Server image is strictly meant for testing and development purposes. Do not use it in production environment! See [X-Road Security Server Sidecar](https://github.com/nordic-institute/X-Road/blob/master/sidecar/SIDECAR.md) for production use.***

The Docker image (`niis/xroad-security-server`) is published on [Docker Hub](https://hub.docker.com/r/niis/xroad-security-server).

All services, including the `serverconf` and `messagelog` PostgreSQL databases, are installed into the same container and run using supervisord.
The installed Security Server is in uninitialized state.

Admin UI credentials: `xrd`/`secret`

## Building the Security Server image

Build the image locally:
```shell
docker build -t xroad-security-server .
```

Alternatively, it's possible to use the image (`niis/xroad-security-server`) available on [Docker Hub](https://hub.docker.com/r/niis/xroad-security-server).

## Running

Publish the container ports (`8080` and/or `8443`, `4000`, and optionally `5500` and `5577`) to localhost (loopback address).
Also, it's possible to pass the token pin code for autologin using the `XROAD_TOKEN_PIN` environment variable.

Running a locally built image:
```shell
docker run -p 127.0.0.1:4000:4000 -p 127.0.0.1:8080:8080 --name my-ss -e XROAD_TOKEN_PIN=1234 xroad-security-server
```

Running an image available on [Docker Hub](https://hub.docker.com/r/niis/xroad-security-server):
```shell
docker run -p 127.0.0.1:4000:4000 -p 127.0.0.1:8080:8080 --name my-ss -e XROAD_TOKEN_PIN=1234 niis/xroad-security-server:focal-7.1.0
```

## Running multiple dockerized X-Road (Security/Central) Servers

If you are running multiple (more than one) containers and map container ports to localhost, it is recommended that you use a separate loopback address for each container and create an X-Road spesific network so that containers can communicate.
Accessing admin-ui of a server from the same domain will break session on other servers. You can get over this by setting multiple mappings to localhost in hosts-file.

```shell
# Create a custom network for X-Road containers, publish by default to localhost only
docker network create -d bridge --opt com.docker.network.bridge.host_binding_ipv4=127.0.0.1 x-road-network

# Create more than one Security Server containers and (optionally) assign them a network-alias for easier reference
docker run -p 4001:4000 -p 8081:8080 --network x-road-network --name my-ss1 xroad-security-server
docker run -p 4002:4000 -p 8082:8080 --network x-road-network --name my-ss2 xroad-security-server
```

## Running multiple Security Servers via docker-compose

`docker-compose.yml` example file:

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

The `xroad-autologin` add-on is installed, but there is no default PIN set, so the following error at startup is normal:

```text
... INFO exited: xroad-autologin (exit status 0; not expected)
... INFO gave up: xroad-autologin entered FATAL state, too many start retries too quickly
```

One can create the autologin file by hand after initializing the Security Server:

```shell
docker exec my-ss su -c 'echo 1234 >/etc/xroad/autologin' xroad
docker exec my-ss supervisorctl start xroad-autologin
```
