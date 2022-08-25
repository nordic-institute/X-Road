FROM ubuntu:20.04 AS ubuntu-base-nvm
SHELL ["/bin/bash", "-c"]
ENV DEBIAN_FRONTEND=noninteractive
ENV NVM_DIR /usr/local/nvm

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl \
  && mkdir /usr/local/nvm \
  && (curl --silent -o- https://raw.githubusercontent.com/creationix/nvm/master/install.sh | bash) \
  && apt-get -qq autoremove \
  && apt-get -qq clean

FROM ubuntu-base-nvm AS ubuntu-base-node
ARG NODE_VERSION=14.18.2

RUN source $NVM_DIR/nvm.sh \
    && nvm install $NODE_VERSION \
    && nvm use $NODE_VERSION --default

ENV NODE_PATH $NVM_DIR/v$NODE_VERSION/lib/node_modules
ENV PATH $NVM_DIR/versions/node/v$NODE_VERSION/bin:$PATH

FROM ubuntu-base-node

WORKDIR /app
COPY ./shared-ui/niis-shared-ui-*.tgz ./shared-ui/audit-ci-shared.json /shared-ui/
COPY ./centralserver/admin-ui/package.json ./centralserver/admin-ui/package-lock.json ./
RUN ls  -al /shared-ui
RUN ls  -al
RUN npm ci --include=dev

COPY ./centralserver/admin-service/src/main/resources/openapi-definition.yaml /admin-service/src/main/resources/openapi-definition.yaml
COPY ./common-rest-api/src/main/resources/common-openapi-definition.yaml /common-rest-api/src/main/resources/common-openapi-definition.yaml
COPY ./centralserver/admin-ui ./

#RUN chown -R "$(whoami):$(whoami)" /app \
    #&& rm -rf src/openapi-types \
    #&& npm run generate-types


