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

Liquibase is the only thing that creates or changes these tables. EDC's own automatic table creation
(`edc.sql.schema.autocreate`) stays off. A missing table shows at the first query, not at boot.

Each baseline holds one changeset per EDC store `*-schema.sql` file, written as typed changesets
(`createTable`, `createIndex`, `remarks`). A raw `<sql>` changeset is used only where a typed one cannot
express the statement. Each changeset's `<comment>` names the EDC store module and file it came from.

## Upgrading EDC

Every EDC version bump (`edc` in `src/gradle/libs.versions.toml`) needs these steps. The
`XRoadContractNegotiationStoreCanaryTest` version check fails on every bump and points here.

1. **List the stores each component loads.** Take the `edc-sql-*`, `edc-store-*`, `edc-ih-sql-*` and
   `edc-issuer-sql-*` dependencies in `ds-control-plane-db/build.gradle.kts`,
   `ds-identity-hub-db/build.gradle.kts` and `ds-issuer-service-application/build.gradle.kts`.
   In the new EDC tag, a store module that queues a table script calls
   `addStatementFromResource` with its `*-schema.sql` file:

   ```
   git -C <Connector or IdentityHub checkout> grep -n addStatementFromResource -- '*.java'
   ```

   Stores added or dropped by the bump change this list, so rebuild it for every bump.

2. **Diff the table scripts between the old and the new tag.** In the `Connector` and `IdentityHub`
   checkouts of the EDC sources, for every store from step 1:

   ```
   git diff <old-tag> <new-tag> -- '*/src/main/resources/*-schema.sql'
   ```

   - Connector stores also carry "Migrate to ..." notes in their `README.md` (contract negotiation, jti,
     cel); read them next to the diff.
   - IdentityHub has no migration notes: the `*-schema.sql` diff is the only source there.
   - A store that now loads a new `*-schema.sql` file adds a new table: treat it as a difference too.

3. **Add changesets for each difference.** Add a new file next to `000-baseline.xml` in every affected
   component directory (for example `ds-control-plane/001-<description>.xml`) and `<include>` it from the
   component's `ds-*-changelog.xml` after the previous file.
   - Use typed changes (`createTable`, `addColumn`, `modifyDataType`, `createIndex`, `dropColumn`, ...).
   - One changeset per EDC script, with a `<comment>` naming the EDC version and file.
   - Never edit an applied changeset, including the baseline: its checksum is stored in `databasechangelog`.
   - A store used by several components (jti, participant context, key pairs, ...) changes in each
     component's directory.
   - `ds-issuer-service` runs in the schema `"ds-issuer-service"`; the changesets must not name a schema.

4. **Verify the schema against EDC's own scripts.** For each component, on a throwaway PostgreSQL:

   1. Create two empty databases, `ref` and `lb`. For `ds-issuer-service`, create the schema
      `"ds-issuer-service"` in both.
   2. In `ref`, run the component's `*-schema.sql` files from the new EDC tag with `psql`, with
      `SET search_path` set to the component schema (`public`, or `"ds-issuer-service",public`).
   3. In `lb`, run the executor twice with `update`. For `ds-issuer-service` use the URL parameter
      `currentSchema=ds-issuer-service,public` and `--defaultSchemaName=ds-issuer-service`.
      The second run must apply nothing: the `databasechangelog` row count does not change.
   4. Dump both databases and compare:

      ```
      pg_dump -s --no-owner --no-privileges -T '*.databasechangelog' -T '*.databasechangeloglock' <db>
      ```

      Drop the `\restrict` and `\unrestrict` lines (random tokens in recent `pg_dump` versions) and trailing
      spaces before the closing quote of `COMMENT ON` lines before the `diff`. The `diff` must be empty.
   5. For `ds-issuer-service`, no EDC table may exist in `public`.

5. **Run the EDC store tests** of the X-Road modules that load the stores
   (`:service:ds-control-plane:ds-xroad-contract-negotiation-store:test`), then update the
   `EXPECTED_*` constants of `XRoadContractNegotiationStoreCanaryTest` as that test describes.

Existing installations apply the new changesets through the normal `update` run: package configure for
native installs, the init services in Compose, the init Jobs in Helm.
