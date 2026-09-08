ARG DB_INIT_IMG
FROM ${DB_INIT_IMG}
COPY test-data/op-monitor-int-test-changelog.xml /app/liquibase/op-monitor-int-test-changelog.xml
COPY test-data/baseline-intTest.xml /app/liquibase/baseline-intTest.xml
