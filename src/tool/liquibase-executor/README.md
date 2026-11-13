# Liquibase executor

Runs the X-Road Liquibase changelogs from `src/main/resources/liquibase/`. One changelog per database:
`--changelog=<name>` runs `liquibase/<name>-changelog.xml`.

## EDC table changelogs

The three dataspace changelogs create the tables of the EDC (Eclipse Dataspace Components) SQL stores:

| Changelog | Component | Baseline file |
|---|---|---|
| `ds-control-plane` | control plane | `ds-control-plane/000-baseline.xml` |
| `ds-identity-hub` | identity hub | `ds-identity-hub/000-baseline.xml` |
| `ds-issuer-service` | Issuer Service | `ds-issuer-service/000-baseline.xml` |

The control plane also carries `ds-control-plane/001-xroad-additions.xml`: an index that X-Road adds and EDC
does not ship (see "Upgrading EDC", step 3).

Liquibase is the only thing that creates or changes these tables. EDC's table-creation extension
(`SqlSchemaBootstrapperExtension`) is excluded in all three runtimes, so the setting
`edc.sql.schema.autocreate` has no effect. A missing table shows at the first query, not at boot.

The baselines assume an empty schema. A database that EDC's automatic creation filled earlier fails with
`relation already exists` on a native install, in Compose and in Helm. Recreate such dev and test databases.

Schema per deployment:
- Control plane and identity hub: on a native Security Server, schema `ds-control-plane` or `ds-identity-hub`
  in a database of the same name. On Helm and Compose, `public` in that database.
- Issuer Service: on a native Central Server, schema `ds-issuer-service` in the Central Server database, with
  the URL parameter `currentSchema=ds-issuer-service,public`. On Helm and Compose, `public` in its own database.

Each baseline holds one changeset per EDC store `*-schema.sql` file, written as typed changesets
(`createTable`, `createIndex`, `remarks`). A raw `<sql>` changeset is used only where a typed one cannot
express the statement. Each changeset's `<comment>` names the EDC store module and file it came from.

## Upgrading EDC

Every EDC version bump (`edc` in `src/gradle/libs.versions.toml`) needs these steps. The
`XRoadContractNegotiationStoreCanaryTest` version check fails on every bump and points here.

1. **List the stores each component loads.** EDC is built from two repositories,
   [Connector](https://github.com/eclipse-edc/Connector) and
   [IdentityHub](https://github.com/eclipse-edc/IdentityHub). Check out both at the tag `v<version>`, with
   `<version>` the `edc` value in `src/gradle/libs.versions.toml` (for example `v1.0.0-RC6`).
   Take the dependencies whose artifact in `src/gradle/libs.versions.toml` ends in `-store-sql` in
   `ds-control-plane-db/build.gradle.kts`, `ds-identity-hub-db/build.gradle.kts` and
   `ds-issuer-service-application/build.gradle.kts`. The `sql-core`, `sql-pool` and `sql-lease` modules
   create no tables.
   In the new EDC tag, a store module that queues a table script calls
   `addStatementFromResource` with its `*-schema.sql` file:

   ```
   git -C <Connector or IdentityHub checkout> grep -n addStatementFromResource -- '*.java'
   ```

   Stores added or dropped by the bump change this list, so rebuild it for every bump.

   `SqlSchemaBootstrapperExtension` is excluded in the `application.yaml` of all three runtimes and replaced by
   `XRoadNoOpSqlSchemaBootstrapperExtension`, so these `addStatementFromResource` calls run no SQL. Check that the
   exclusion still names the class in the new tag. A new store on the classpath gets its tables only through a
   changeset (step 3).

2. **Diff the table scripts between the old and the new tag.** In the `Connector` and `IdentityHub`
   checkouts of the EDC sources, for every store from step 1:

   ```
   git diff <old-tag> <new-tag> -- '*/src/main/resources/*-schema.sql'
   ```

   - Connector stores also carry "Migrate to ..." notes in their `README.md`. Read them next to the diff.
     Today the stores in use with such notes are contract negotiation, transfer process, jti validation,
     cel and participant context (`participantcontext-store-sql`). Find them for the new tag with:
     ```
     grep -rIn -i 'migrate to' --include=README.md <Connector checkout>/extensions
     ```
   - IdentityHub has no migration notes: the `*-schema.sql` diff is the only source there.
   - A store that now loads a new `*-schema.sql` file adds a new table: treat it as a difference too.

3. **Add changesets for each difference.** Add a new file next to `000-baseline.xml` in every affected
   component directory (for example `ds-control-plane/NNN-<description>.xml`, with `NNN` the next unused
   number) and `<include>` it from the component's `ds-*-changelog.xml` after the previous file.
   - A new file copies the header and attributes of `000-baseline.xml`: the `databaseChangeLog` element with
     `logicalFilePath` set to the new file's path under `liquibase/`, and every `changeSet` with
     `author="niis"` and `objectQuotingStrategy="QUOTE_ALL_OBJECTS"`.
   - Use typed changes (`createTable`, `addColumn`, `modifyDataType`, `createIndex`, `dropColumn`, ...).
   - One changeset per EDC script, with a `<comment>` naming the EDC version and file.
   - Exception: an X-Road addition that EDC does not ship (for example an extra index) goes in a separate
     `NNN-xroad-additions.xml` file, with a `<comment>` that says it is an X-Road addition, and a matching
     statement in the component's additions file
     `src/tool/liquibase-executor/src/test/resources/parity/<component>-additions.sql` (step 4.3). A later
     addition is a new changeset in a new `NNN-xroad-additions.xml` file. Only `ds-control-plane` has one so far.
   - Retiring an addition: at every EDC upgrade, check each statement of the additions file against the new
     tag. If EDC now ships an index for it:
     - same name and definition as the addition: remove the statement from the additions file and add no
       changeset;
     - otherwise: drop the X-Road index in a new `NNN-xroad-additions.xml` changeset (the applied one stays
       unchanged), add EDC's index as above, and remove the statement from the additions file.
   - Never edit an applied changeset, including the baseline: its checksum is stored in `databasechangelog`.
   - A store used by several components (jti, participant context, key pairs, ...) changes in each
     component's directory.
   - Changesets never name a schema: `ds-issuer-service` runs in the schema `"ds-issuer-service"` on a native
     Central Server and in `public` elsewhere.

4. **Verify the schema against EDC's own scripts.** Run this for each component on a throwaway
   PostgreSQL. The commands below are for the identity hub; run them from the root of the core repository, with `docker`,
   `java` and `openssl` available. Set `EDC_SRC` to the directory that holds the `Connector` and `IdentityHub`
   checkouts at the new tag.
   1. Build the executor jar and start PostgreSQL with a generated password:
      ```
      src/gradlew -p src :tool:liquibase-executor:shadowJar
      export PGPASSWORD="$(openssl rand -hex 12)"
      docker run -d --rm --name edc-check -e POSTGRES_PASSWORD="$PGPASSWORD" -p 127.0.0.1:55432:5432 postgres:18
      ```
      Wait until `docker exec edc-check pg_isready -U postgres` succeeds. The image restarts the server once
      after it initialises, so wait a few seconds more and check again.
   2. Create two empty databases, `ref` and `lb`:
      ```
      for db in ref lb; do docker exec edc-check psql -q -U postgres -c "create database $db"; done
      ```
      For `ds-issuer-service` on the native layout, also create the schema in both:
      `docker exec edc-check psql -q -U postgres -d <db> -c 'create schema "ds-issuer-service"'`.
   3. In `ref`, run the `*-schema.sql` files of the component's stores (step 1) from the new EDC tag, with
      `search_path` set to the component schema (`public`, or `"ds-issuer-service",public`). Identity hub:
      ```
      IH="$EDC_SRC/IdentityHub/extensions/store/sql"
      CN="$EDC_SRC/Connector/extensions"
      for f in \
        "$IH/identity-hub-credentials-store-sql/src/main/resources/credentials-schema.sql" \
        "$IH/identity-hub-did-store-sql/src/main/resources/did-schema.sql" \
        "$IH/identity-hub-keypair-store-sql/src/main/resources/keypairs-schema.sql" \
        "$IH/holder-credential-request-store-sql/src/main/resources/holder-credential-request-schema.sql" \
        "$IH/holder-credential-offer-store-sql/src/main/resources/credential-offer-schema.sql" \
        "$IH/sts-client-store-sql/src/main/resources/sts-client-schema.sql" \
        "$CN/common/store/sql/jti-validation-store-sql/src/main/resources/jti-validation-schema.sql" \
        "$CN/control-plane/store/sql/participantcontext-store-sql/src/main/resources/participant-context-schema.sql" \
        "$CN/control-plane/store/sql/participantcontext-config-store-sql/src/main/resources/participant-context-config-schema.sql"
      do
        { echo 'SET search_path TO public;'; cat "$f"; } |
          docker exec -i edc-check psql -v ON_ERROR_STOP=1 -q -U postgres -d ref || break
      done
      ```
      Then apply the component's X-Road additions file, if it has one, so the diff in 4.5 stays empty. For
      `ds-control-plane`:
      ```
      { echo 'SET search_path TO public;'; cat src/tool/liquibase-executor/src/test/resources/parity/ds-control-plane-additions.sql; } |
        docker exec -i edc-check psql -v ON_ERROR_STOP=1 -q -U postgres -d ref
      ```
      When EDC now ships an addition, retire it as step 3 says.
   4. In `lb`, run the executor twice with `update`. The password goes through the environment, not the
      command line. The second run must apply nothing: the `databasechangelog` row count does not change.
      Logback errors about `/var/log/xroad` on a machine without X-Road are harmless.
      ```
      run() {
        LIQUIBASE_COMMAND_PASSWORD="$PGPASSWORD" java -jar src/tool/liquibase-executor/build/libs/liquibase-executor.jar \
          --changelog=ds-identity-hub --url=jdbc:postgresql://127.0.0.1:55432/lb --username=postgres \
          --defaultSchemaName=public --contexts=user update
      }
      count() { docker exec edc-check psql -U postgres -d lb -tA -c 'select count(*) from databasechangelog'; }
      run && count && run && count
      ```
      For `ds-issuer-service` on the native layout, change the arguments to `--changelog=ds-issuer-service`,
      `--url='jdbc:postgresql://127.0.0.1:55432/lb?currentSchema=ds-issuer-service,public'` and
      `--defaultSchemaName=ds-issuer-service`, and count the rows of `"ds-issuer-service".databasechangelog`.
   5. Dump both databases and compare. The `diff` must print nothing:
      ```
      dump() {
        docker exec edc-check pg_dump -s --no-owner --no-privileges \
          -T '*.databasechangelog' -T '*.databasechangeloglock' -U postgres "$1" |
          grep -v -E '^\\(un)?restrict ' | sed -E "/^COMMENT ON /s/ +';\$/';/"
      }
      diff -u <(dump ref) <(dump lb)
      ```
      The `grep` drops the `\restrict` and `\unrestrict` lines (random tokens in recent `pg_dump` versions).
      The `sed` removes trailing spaces before the closing quote of `COMMENT ON` lines.
   6. For `ds-issuer-service` in its native layout (schema `ds-issuer-service`), no EDC table may exist in `public`:
      `docker exec edc-check psql -U postgres -d lb -tA -c "select count(*) from pg_tables where schemaname = 'public'"`
      prints `0`.
   7. Remove the container: `docker rm -f edc-check`.

5. **Run the EDC store tests** of the X-Road modules that load the stores
   (`:service:ds-control-plane:ds-xroad-contract-negotiation-store:test`), then update the
   `EXPECTED_*` constants of `XRoadContractNegotiationStoreCanaryTest` as that test describes.

Existing installations apply the new changesets through the normal `update` run: package configure for
native installs, the init services in Compose, the init Jobs in Helm.
