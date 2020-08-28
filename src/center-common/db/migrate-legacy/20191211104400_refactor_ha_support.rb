# Remove BDR dependency from CS HA support (XRDDEV-760)
class RefactorHaSupport < ActiveRecord::Migration

  def up
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"

      # fix schema_migrations is missing a primary key (update/delete does not work with logical replication)
      execute("alter table schema_migrations add primary key(version);")
      execute("drop index if exists unique_schema_migrations;")

      version = ActiveRecord::Base.connection.select_value("SELECT current_setting('server_version_num')")
      if version.to_i() >= 90600
        #A bit more efficient version for a more recent PostgreSQL
        execute <<-SQL
CREATE OR REPLACE FUNCTION insert_node_name()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
  current_ha_node_name text;
BEGIN
  current_ha_node_name := current_setting('xroad.current_ha_node_name', true);
  IF current_ha_node_name IS NOT NULL THEN
    NEW.ha_node_name := current_ha_node_name;
  ELSE
    NEW.ha_node_name := 'node_0';
  END IF;
  RETURN NEW;
END
$function$
      SQL
      else
        # legacy version, need to catch exception (more expensive) if current_setting fails
        execute <<-SQL
CREATE OR REPLACE FUNCTION insert_node_name()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
  current_ha_node_name text;
BEGIN
  BEGIN
    current_ha_node_name := current_setting('xroad.current_ha_node_name');
  EXCEPTION
    WHEN OTHERS THEN
      current_ha_node_name := NULL;
  END;
  IF current_ha_node_name IS NOT NULL THEN
    NEW.ha_node_name := current_ha_node_name;
  ELSE
    NEW.ha_node_name := 'node_0';
  END IF;
  RETURN NEW;
END
$function$
        SQL
      end
    end

    # add a view for cluster status monitoring (works also for single-node setup)
    execute <<-SQL
CREATE VIEW ha_cluster_status AS (
SELECT sp.ha_node_name,
    sp.value AS address,
    df.configuration_generated
   FROM system_parameters sp
     LEFT JOIN ( SELECT distributed_files.ha_node_name,
            max(distributed_files.file_updated_at) AS configuration_generated
           FROM distributed_files
          WHERE distributed_files.content_identifier::text = 'PRIVATE-PARAMETERS'
          GROUP BY distributed_files.ha_node_name) df ON sp.ha_node_name::text = df.ha_node_name::text
  WHERE sp.key = 'centralServerAddress');
SQL
  end

  def down
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
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
    end
    execute "DROP VIEW ha_cluster_status;"
  end

end
