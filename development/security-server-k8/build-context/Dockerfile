FROM eclipse-temurin:21-jre AS builder

WORKDIR /builder

ARG JAR_FILE

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Create a runtime container
FROM xroad-ss-baseline-runtime:latest
WORKDIR /opt/app

# Copy Spring boot layers
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

# Copy service specific files to image
COPY --from=service-ctx --chown=xroad:xroad /etc /etc
COPY --from=service-ctx --chown=xroad:xroad /usr /usr

#Set default environment variables
ENV XROAD_DEPLOYMENT_TYPE=containerized
ENV XROAD_APPLICATION_TYPE=ss

USER xroad

CMD ["/opt/app/entrypoint.sh"]

EXPOSE 5580
