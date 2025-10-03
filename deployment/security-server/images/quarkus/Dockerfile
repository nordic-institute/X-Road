ARG REGISTRY=localhost:5555
ARG IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime

FROM ${REGISTRY}/${BASE_IMAGE}:${IMAGE_TAG}

COPY --from=build-artifacts --chown=xroad:xroad / /opt/app/
COPY --from=entrypoint --chmod=755 /entrypoint.sh /opt/app/entrypoint.sh

WORKDIR /opt/app
USER xroad

ENTRYPOINT ["/bin/sh", "/opt/app/entrypoint.sh"]

