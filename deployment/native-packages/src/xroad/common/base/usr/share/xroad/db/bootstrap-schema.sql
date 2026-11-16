-- Database-level bootstrap: revokes public access, creates the component schema, sets the `public` schema
-- permissions and grants connect rights. Run as the database master or the database owner, after
-- bootstrap-roles.sql. Safe to rerun.
--
-- Input is the same psql variables as bootstrap-roles.sql. Only db_name, admin_role, runtime_role,
-- schema_name, hstore and single_role are read here.
-- When schema_name is public, no schema is created: the owner role takes over public, so it can create tables there.
\set ON_ERROR_STOP on

\if :single_role
  \set owner_role :runtime_role
\else
  \set owner_role :admin_role
\endif

\c :"db_name"

REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CREATE, TEMPORARY, CONNECT ON DATABASE :"db_name" TO :"owner_role";
GRANT TEMPORARY, CONNECT ON DATABASE :"db_name" TO :"runtime_role";

SELECT :'schema_name' = 'public' AS schema_is_public \gset
\if :schema_is_public
  ALTER SCHEMA public OWNER TO :"owner_role";
\else
  CREATE SCHEMA IF NOT EXISTS :"schema_name" AUTHORIZATION :"owner_role";
\endif

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"owner_role";
GRANT USAGE ON SCHEMA public TO :"runtime_role";

\if :hstore
  CREATE EXTENSION IF NOT EXISTS hstore SCHEMA public;
\endif
