FROM ubuntu:noble

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get -qq install wget  && \
    ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then \
        ARCH="x86_64"; \
    elif [ "$ARCH" = "aarch64" ]; then \
        ARCH="aarch64"; \
    else \
        echo "Unsupported architecture: $ARCH" && exit 1; \
    fi && \
    echo "Architecture detected: $ARCH" && \
    mkdir -p /opt && \
    wget -O "/opt/payloadgen-${ARCH}" "https://artifactory.niis.org/artifactory/xroad-generic-utils/payloadgen-${ARCH}" || \
    (echo "Failed to download payloadgen binary for architecture: $ARCH" && exit 1) && \
    chmod +x "/opt/payloadgen-${ARCH}" && \
    if [ ! -f "/opt/payloadgen-${ARCH}" ] || [ ! -x "/opt/payloadgen-${ARCH}" ]; then \
        echo "Downloaded file is not executable or does not exist" && exit 1; \
    fi && \
    ln -sf "/opt/payloadgen-${ARCH}" /opt/payloadgen

# Configure rocket to listen on all interfaces
ENV ROCKET_ADDRESS=0.0.0.0
ENV ROCKET_PORT=8080

EXPOSE 8080

CMD ["/bin/sh", "-c", "/opt/payloadgen"]
