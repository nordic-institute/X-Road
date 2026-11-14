-- Cluster-level database bootstrap: creates the database, the owner role and the runtime role.
-- Run as the database master. CREATEDB and CREATEROLE are enough, no superuser is needed.
-- Safe to rerun: existing objects are kept, and an existing role keeps its password.
-- Run bootstrap-database.sql after this file.
--
-- Input is psql variables, set before this file as `\set name 'value'` (double every ' and \ in the value):
--   db_name          database to create
--   admin_role       owner role
--   admin_password   its password, ignored when single_role is on
--   runtime_role     role the application connects as
--   runtime_password its password
--   master_role      role running this file
--   schema_name      component schema, used by bootstrap-database.sql
--   hstore           on or off, used by bootstrap-database.sql
--   single_role      on when the runtime role is also the owner, so no admin role is created
\set ON_ERROR_STOP on

\if :single_role
  \set owner_role :runtime_role
\else
  \set owner_role :admin_role
\endif

SELECT format('CREATE DATABASE %I ENCODING ''UTF8''', :'db_name')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db_name')
\gexec

\if :single_role
\else
  SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'admin_role', :'admin_password')
  WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'admin_role')
  \gexec
\endif

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'runtime_role', :'runtime_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'runtime_role')
\gexec

GRANT :"owner_role" TO :"master_role";
GRANT :"runtime_role" TO :"master_role";
