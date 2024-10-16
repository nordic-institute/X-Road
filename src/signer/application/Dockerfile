FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder

ARG JAR_FILE=build/libs/*.jar

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Create a runtime container
FROM eclipse-temurin:21-jre
WORKDIR /opt/app

RUN groupadd --system --gid 999 xroad && \
    useradd --system --uid 999 --no-create-home --shell /usr/sbin/nologin --gid xroad xroad

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

RUN mkdir /var/log/xroad && \
    chown -R xroad:xroad /opt/app && \
    chown -R xroad:xroad /var/log/xroad

USER xroad:xroad

ENTRYPOINT ["java", "-jar", "application.jar"]

EXPOSE 5580
