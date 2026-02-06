FROM liquibase:4.33.0

# Optimize APT for flaky mirrors (e.g. ports.ubuntu.com)
USER root
RUN echo 'Acquire::Connect::Timeout "5";' > /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::http::Timeout "10";' >> /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::https::Timeout "10";' >> /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::Retries "3";' >> /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::ForceIPv4 "true";' > /etc/apt/apt.conf.d/99force-ipv4

# Build arg for the changelog file name (e.g., "serverconf-changelog.xml")
ARG CHANGELOG_FILE

# Liquibase configuration
ENV LIQUIBASE_COMMAND_CHANGELOG_FILE="changelog/${CHANGELOG_FILE}" \
    LIQUIBASE_COMMAND_DRIVER="org.postgresql.Driver"

COPY --from=build-artifacts --chown=liquibase:liquibase / /liquibase/changelog/

COPY --from=license --chown=liquibase:liquibase /LICENSE.txt /liquibase/changelog/LICENSE.txt
COPY --from=license --chown=liquibase:liquibase /3RD-PARTY-NOTICES.txt /liquibase/changelog/3RD-PARTY-NOTICES.txt

USER liquibase
WORKDIR /liquibase

CMD ["--log-level=debug", "update"]
