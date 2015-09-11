# Migration for adding support for HA to the schema of the database of the central
# server. Note that the dependecies and the required version of Postgres must be
# installed and configured before running this migration on a Postgres database.
# For SQLite-based tests, HA support is almost no-op.
# NOTE: This migration is not built for upgrading existing databases where
# the sequences have already been used, for instance, and references between
# tables exist.
class AsyncMultimasterSupport < ActiveRecord::Migration

  TABLES_USING_NODE_NAME = [
    "configuration_sources",
    "distributed_files",
    "history",
    "system_parameters",
  ]

  def up
    # Add the ha_node_name field to tables where this distinction is
    # necessary in HA setups. In a single-database setup the default value
    # will be used at the trigger level (in production) or the field will
    # be left empty (in SQLite-based tests).
    for table_name in TABLES_USING_NODE_NAME
      add_column :"#{table_name}", :ha_node_name, :string
    end

    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
ALTER TABLE configuration_sources ADD CONSTRAINT unique_type UNIQUE(source_type, ha_node_name);
ALTER TABLE distributed_files ADD CONSTRAINT unique_name UNIQUE(file_name, ha_node_name);

CREATE OR REPLACE FUNCTION insert_node_name() RETURNS trigger AS $body$
DECLARE
  current_bdr_node_name text;
BEGIN
  current_bdr_node_name := NULL;
  IF exists(SELECT 1 FROM pg_extension WHERE extname='bdr') THEN
    current_bdr_node_name := bdr.bdr_get_local_node_name();
  END IF;
  IF current_bdr_node_name IS NOT NULL THEN
    NEW.ha_node_name := current_bdr_node_name;
  ELSE
    -- Expecting the server is configured properly, we don't want a NULL
    -- fallback value here.
    NEW.ha_node_name := 'node_0';
  END IF;
  RETURN NEW;
END
$body$
LANGUAGE 'plpgsql';
      SQL

      for table_name in TABLES_USING_NODE_NAME
        execute <<-SQL
DO
$do$
BEGIN
  IF exists(SELECT 1 FROM pg_extension WHERE extname='bdr') THEN
    update #{table_name} set ha_node_name = bdr.bdr_get_local_node_name();
  ELSE
    update #{table_name} set ha_node_name = 'node_0';
  END IF;
END
$do$
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS insert_node_name ON #{table_name};
CREATE TRIGGER insert_node_name BEFORE INSERT ON #{table_name}
  FOR EACH ROW EXECUTE PROCEDURE insert_node_name();
        SQL
      end
    end
  end

  def down
    for table_name in TABLES_USING_NODE_NAME
      remove_column :"#{table_name}", :ha_node_name
    end

    execute <<-SQL
ALTER TABLE configuration_sources DROP CONSTRAINT IF EXISTS unique_type;
ALTER TABLE distributed_files DROP CONSTRAINT IF EXISTS unique_name;
    SQL

    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      for table_name in TABLES_USING_NODE_NAME
        execute <<-SQL
DROP TRIGGER IF EXISTS insert_node_name ON #{table_name};
        SQL
      end
      execute <<-SQL
DROP FUNCTION IF EXISTS insert_node_name();
      SQL
    end
  end
end
