################################################################################
FROM rust:1.79-alpine AS build

RUN apk add --no-cache musl-dev #clang lld

RUN --mount=type=bind,source=src,target=src \
    --mount=type=bind,source=Cargo.toml,target=Cargo.toml \
    --mount=type=bind,source=Cargo.lock,target=Cargo.lock \
    --mount=type=cache,target=/app/target/ \
   cargo build --release && \
   cp ./target/release/payloadgen /bin/payloadgen

################################################################################
FROM alpine:3.18 AS final

# Copy the executable from the "build" stage.
COPY --from=build /bin/payloadgen /

# Configure rocket to listen on all interfaces.
ENV ROCKET_ADDRESS=0.0.0.0
#ENV ROCKET_KEEP_ALIVE=0
ENV ROCKET_PORT=8080

EXPOSE 8080

CMD ["/payloadgen"]
