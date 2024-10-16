FROM eclipse-temurin:21-jre AS builder

WORKDIR /builder

ARG JAR_FILE

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Create a runtime container
FROM eclipse-temurin:21-jre
WORKDIR /opt/app

#TODO Consider this beinbg a separate base image. In any case, layer is cached
RUN \
      --mount=type=cache,target=/var/cache/apt,sharing=locked \
      --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get -qq update &&  \
    apt-get install --no-install-recommends -qq -y gnupg

RUN groupadd --system --gid 999 xroad && \
    useradd --system --uid 999 --no-create-home --shell /usr/sbin/nologin --gid xroad xroad

#TODO temporary users for PAM based auth
RUN usermod --append -G shadow xroad && \
    groupadd --system xroad-system-administrator && \
    groupadd --system xroad-security-officer && \
    groupadd --system xroad-registration-officer && \
    groupadd --system xroad-service-administrator && \
    groupadd --system xroad-securityserver-observer && \
    useradd -m xrd -s /usr/sbin/nologin -p '$6$JeOzaeWnLAQSUVuO$GOJ0wUKSVQnOR4I2JgZxdKr.kMO.YGS21SGaAshaYhayv8kSV9WuIFCZHTGAX8WRRTB/2ojuLnJg4kMoyzpcu1' && \
    usermod -a -G xroad-system-administrator xrd && \
    usermod -a -G xroad-security-officer xrd && \
    usermod -a -G xroad-registration-officer xrd && \
    usermod -a -G xroad-service-administrator xrd && \
    usermod -a -G xroad-securityserver-observer xrd

#Copy Spring boot layers
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

ARG ENTRYPOINT_SCRIPT
#Copy additional entrypoint files
COPY --from=entrypoint-ctx /${ENTRYPOINT_SCRIPT} /opt/app/entrypoint.sh
COPY --from=entrypoint-ctx /generate_certificate.sh /opt/app/scripts/generate_certificate.sh

#TODO for now its file by file, for better visibility. Simplify later
COPY --from=entrypoint-ctx /openssl.cnf /opt/app/scripts/openssl.cnf

RUN mkdir /var/log/xroad && \
    mkdir /var/tmp/xroad/ && \
    mkdir /opt/app/cache && \
    mkdir -p /etc/xroad/ssl && \
    mkdir -p /etc/xroad/globalconf && \
    chown -R xroad:xroad /opt/app && \
    chown -R xroad:xroad /var/log/xroad && \
    chown -R xroad:xroad /var/tmp/xroad/ && \
    chown -R xroad:xroad /etc/xroad

USER xroad

CMD ["/opt/app/entrypoint.sh"]

EXPOSE 5580
