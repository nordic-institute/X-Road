ARG SIGNER_IMG

FROM $SIGNER_IMG

USER root

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends curl \
  && apt-get -qq clean

USER xroad

EXPOSE 5560
