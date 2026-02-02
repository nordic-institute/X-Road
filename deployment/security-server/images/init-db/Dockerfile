FROM liquibase:4.33.0

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
