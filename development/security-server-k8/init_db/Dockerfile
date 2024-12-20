FROM liquibase/liquibase:4.30

ARG CHANGELOG_FILE
ENV CHANGELOG_FILE=${CHANGELOG_FILE}

COPY --from=changelog --chown=liquibase:liquibase / /liquibase/changelog/
COPY entrypoint.sh /entrypoint.sh

COPY --chown=liquibase:liquibase entrypoint.sh /entrypoint.sh
RUN chmod 0755 /entrypoint.sh || true

USER liquibase

ENTRYPOINT ["/entrypoint.sh"]