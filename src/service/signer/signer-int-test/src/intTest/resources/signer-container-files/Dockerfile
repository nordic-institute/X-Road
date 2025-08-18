FROM eclipse-temurin:21-jre-noble

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends curl softhsm2 \
  && apt-get -qq clean

# Create token
RUN mkdir -p /var/lib/softhsm/tokens/ && \
    softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 1234

COPY --chown=root:root files/app.jar /root/app.jar
COPY --chown=root:root files/lib /root/lib

COPY --chown=root:root etc /etc
COPY --chown=root:root var /var

EXPOSE 5588 5559 5560
