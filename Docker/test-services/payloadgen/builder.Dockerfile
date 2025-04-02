FROM rust:1.85

# Install cross-compilation tools with all necessary dependencies
RUN apt-get update && apt-get install -y \
    gcc-aarch64-linux-gnu \
    g++-aarch64-linux-gnu \
    libc6-dev-arm64-cross

# Set up environment variables for cross-compilation
ENV CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER=gcc
ENV CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc
ENV CC_aarch64_unknown_linux_gnu=aarch64-linux-gnu-gcc
ENV CXX_aarch64_unknown_linux_gnu=aarch64-linux-gnu-g++

# Install Rust targets and verify installation
# Use the host target for x86_64 instead of specifying unknown-linux-gnu
RUN rustup target add aarch64-unknown-linux-gnu && \
    rustup target list --installed

# Create a new empty project
WORKDIR /opt/payloadgen
#COPY . .
#
## Build for x86_64 (using the default host target)
#RUN cargo build --release
#
## Build for arm64
#RUN cargo build --release --target aarch64-unknown-linux-gnu