# Liquibase executor

Runs the X-Road Liquibase changelogs from `src/main/resources/liquibase/`. One changelog per database:
`--changelog=<name>` runs `liquibase/<name>-changelog.xml`.

## EDC table changelogs

`ds-control-plane`, `ds-identity-hub` and `ds-issuer-service` create the tables of the EDC (Eclipse Dataspace
Components) SQL stores. Each has a `<name>/000-baseline.xml` with one changeset per EDC `*-schema.sql` file. The
`<comment>` of a changeset names the EDC version, store module and file.

- Liquibase is the only thing that creates or changes these tables. EDC's `SqlSchemaBootstrapperExtension` is
  excluded in all three runtimes, so `edc.sql.schema.autocreate` has no effect. A missing table shows at the first
  query, not at boot.
- The baselines need an empty schema. A dev or test database that EDC filled earlier fails with
  `relation already exists`: recreate it.
- Schema: on a native install it has the name of the changelog by default. For the Issuer Service it is in the
  Central Server database (URL parameter `currentSchema=ds-issuer-service,public`). On Helm and Compose it is
  `public`.

## Upgrading EDC

Do this at every change of `edc` in `src/gradle/libs.versions.toml`.

1. **Diff EDC's table scripts** between the old and the new tag, in the Connector and IdentityHub sources:

   ```
   git diff <old-tag> <new-tag> -- '*/src/main/resources/*-schema.sql'
   ```

   - Only the stores named in the baseline `<comment>`s matter.
   - A store that is new on a runtime's classpath needs a changeset for its tables too. The stores are the
     `-store-sql` dependencies of `ds-control-plane-db`, `ds-identity-hub-db` and `ds-issuer-service-application`.
   - The diff does not show renames or data moves. Connector describes them under "Migrate to ..." in the
     `README.md` of each store. IdentityHub has no such notes.
   - Check that `SqlSchemaBootstrapperExtension` still exists under the same name and package at the new tag. The
     exclusion in the three `application.yaml` files silently ignores a name that matches nothing.

2. **Write the changesets.** In each affected component, add `ds-<component>/NNN-<description>.xml` and `<include>`
   it in `ds-<component>-changelog.xml` after the previous file.
   - Copy the header and the `changeSet` attributes of `000-baseline.xml` (`author`, `objectQuotingStrategy`).
     Set `logicalFilePath` to the new file's own path.
   - Use typed changes (`createTable`, `addColumn`, `createIndex`, ...), and raw `<sql>` only where no typed
     change fits.
   - Never edit an applied changeset, including the baseline: Liquibase stores its checksum, so `update` then
     fails on existing databases.
   - Never name a schema: it differs per deployment.
   - A store used by several components (jti validation, participant context, key pairs, ...) changes in the
     directory of each component.
   - An object that EDC does not ship (today one index, `ds-control-plane/001-xroad-additions.xml`) goes in its
     own `NNN-xroad-additions.xml` file. If EDC starts to ship the same object, drop the X-Road one in a new
     changeset.

3. **Compare with EDC.** No test does this yet. On an empty PostgreSQL, build one database with `update` and one
   from EDC's `*-schema.sql` files at the new tag, then diff their `pg_dump -s` output. Only objects from
   `NNN-xroad-additions.xml` files may differ (ignore `databasechangelog*`, `\restrict` and `\unrestrict` lines,
   and trailing spaces in `COMMENT ON`). For the Issuer Service, also run it in the schema `ds-issuer-service`: no
   table may land in `public`.

4. **Fix `XRoadContractNegotiationStoreCanaryTest`.** It fails on every EDC change. Follow its Javadoc.

Existing installations get the new changesets at the next `update` run: package configure on native installs, the
init services in Compose, the init Jobs in Helm.
