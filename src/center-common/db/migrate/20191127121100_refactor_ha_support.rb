# Remove BDR dependency from CS HA support (XRDDEV-760)
class RefactorHaSupport < ActiveRecord::Migration

  def up
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
CREATE OR REPLACE FUNCTION public.insert_node_name()
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
    -- Expecting the server is configured properly, we don't want a NULL
    -- fallback value here.
    NEW.ha_node_name := 'node_0';
  END IF;
  RETURN NEW;
END
$function$
      SQL
    end
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
  end

end
