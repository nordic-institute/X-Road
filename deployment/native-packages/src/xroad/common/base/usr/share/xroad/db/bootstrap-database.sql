-- Database-level bootstrap: revokes public access, creates the component schema, sets the `public` schema
-- permissions and grants connect rights. Run as the database master or the database owner, after
-- bootstrap-cluster.sql. Safe to rerun.
--
-- Input is the same psql variables as bootstrap-cluster.sql. Only db_name, admin_role, runtime_role,
-- schema_name, hstore and single_role are read here.
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

CREATE SCHEMA IF NOT EXISTS :"schema_name" AUTHORIZATION :"owner_role";

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"owner_role";
GRANT USAGE ON SCHEMA public TO :"runtime_role";

\if :hstore
  CREATE EXTENSION IF NOT EXISTS hstore SCHEMA public;
\endif
