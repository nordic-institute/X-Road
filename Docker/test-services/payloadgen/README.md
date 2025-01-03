# REST-y test service

A simple REST-y test service 'payloadgen'. It provides the following endpoints:

* GET /data/_size_  
  Generates a random response of _size_ bytes
* PUT /data    
* POST /data        
  Accepts a request of arbitrary size, responds with the size of the request: `{"size" : 10}`

## Building / Running

```
cargo build --release
env ROCKET_KEEP_ALIVE=0 ROCKET_PORT=8080 target/release/payloadgen
```

Implemented using [Rust 🦀](https://www.rust-lang.org/)

Easiest way to install Rust is probably rustup, see https://www.rust-lang.org/tools/install. In addition, you'll need a linker (e.g. on Ubuntu do `apt install build-essential`). 

Currently requires nightly toolchain due to rocket.rs web framework. Cargo (the Rust package manager) will install it on first build.

Hyper (at least the version Rocket 0.4 uses) has some issues with persistent HTTP connections causing failures e.g. during load-testing. For best interoperability with X-Road, disable keep-alives.

## Building / Running in Docker

Build Docker image:
```
docker build -t payloadgen .
```

Run the Docker container:
```
docker run -it -p 0:8080 payloadgen
```
