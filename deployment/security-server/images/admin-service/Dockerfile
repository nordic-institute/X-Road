ARG REGISTRY=localhost:5555
ARG IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime
FROM ${REGISTRY}/${BASE_IMAGE}:${IMAGE_TAG}

COPY --from=build-artifacts --chown=xroad:xroad /proxy-ui-api-1.0.jar /opt/app/proxy-ui-api.jar
COPY --from=entrypoint --chmod=755 /entrypoint.sh /opt/app/entrypoint.sh

WORKDIR /opt/app
USER xroad

ENTRYPOINT ["/bin/bash", "/opt/app/entrypoint.sh"]

