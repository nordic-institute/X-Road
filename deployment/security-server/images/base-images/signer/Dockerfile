ARG REGISTRY=localhost:5555
ARG BASE_IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime

FROM ${REGISTRY}/${BASE_IMAGE}:${BASE_IMAGE_TAG}

# Copy PKCS#11 wrapper library for hardware security module support
ARG TARGETARCH
COPY --from=pkcs11driver ${TARGETARCH}/libpkcs11wrapper.so /usr/share/xroad/lib/libpkcs11wrapper.so

