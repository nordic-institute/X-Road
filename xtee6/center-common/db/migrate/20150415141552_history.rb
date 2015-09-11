# Table for storing all the inserts, updates and deletions to
# the database as an audit log. The writes to this table will
# be carried out by a Postgres trigger function.
# The up and down methods of this migration are no-ops when unit tests are run
# on top of SQLite.
# XXX Ensure the hstore extension has been installed to the database!
class History < ActiveRecord::Migration

  # Based on this list, the triggers related to the history table are created and
  # dropped in this migration.
  # If new tables are added, triggers must be added and removed in the corresponding
  # migrations.
  TABLES_WITH_HISTORY_SUPPORT = [
    "anchor_url_certs",
    "anchor_urls",
    "approved_cas",
    "approved_tsas",
    "auth_certs",
    "ca_infos",
    "central_services",
    "configuration_signing_keys",
    "configuration_sources",
    # Skip distributed_files and distributed_signed_files because they contain
    # lots of data but only repeat the contents of the other tables.
    "global_group_members",
    "global_groups",
    "identifiers",
    "member_classes",
    "ocsp_infos",
    "request_processings",
    "requests",
    # Skip schema_migrations because this is a meta-table.
    "security_categories",
    "security_server_client_names",
    "security_server_clients",
    "security_servers",
    "security_servers_security_categories",
    "server_clients",
    "trusted_anchors",
    "ui_users",
    "v5_imports",
    "global_groups"
  ]

  def up
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"

      create_table "history", :force => true do |t|
        t.integer  "id"
        t.string   "operation",  :null => false
        t.string   "table_name", :null => false
        t.integer  "record_id",  :null => false
        t.string   "field_name", :null => false
        t.text   "old_value"
        t.text   "new_value"
        t.string   "user_name",  :null => false
        t.datetime "timestamp",  :null => false
      end

      execute <<-SQL
DO $$
BEGIN
  CREATE TYPE changed_field_type AS (field_key text, field_value text);
EXCEPTION WHEN duplicate_object THEN
  -- The type already exists.
END $$
LANGUAGE plpgsql;

-- Insert a single row to the history table.
CREATE OR REPLACE FUNCTION insert_history_row(
  user_name text, operation text, table_name text,
  field_data changed_field_type, old_data hstore, new_data hstore, record_id integer)
RETURNS void AS $body$

DECLARE
  _history_row history;

BEGIN

  _history_row = ROW(
    NEXTVAL('history_id_seq'),
    operation, table_name, record_id, 
    field_data.field_key, -- name of the field that was changed
    NULL, -- old value
    NULL, -- new value
    user_name,
    statement_timestamp()
  );

  IF (operation = 'UPDATE') THEN
    _history_row.old_value = old_data -> field_data.field_key;
    _history_row.new_value = field_data.field_value;
  ELSIF (operation = 'DELETE') THEN
    _history_row.old_value = old_data -> field_data.field_key;
  ELSIF (operation = 'INSERT') THEN
    _history_row.new_value = field_data.field_value;
  END IF;

  INSERT INTO history VALUES (_history_row.*);
END;
$body$
LANGUAGE 'plpgsql';

-- Trigger function for inserting rows to the history table for each INSERT,
-- UPDATE and DELETE operation on the tables that have this trigger set.
CREATE OR REPLACE FUNCTION add_history_rows() RETURNS TRIGGER AS $body$

DECLARE
  _record_id integer;
  _old_data hstore;
  _new_data hstore;
  _changed_fields hstore;
  _field_data changed_field_type;
  _user_name text;
  _operation text;

BEGIN
  IF TG_WHEN <> 'AFTER' THEN
    RAISE EXCEPTION 'add_history_rows() may only be used as an AFTER trigger';
  END IF;

  IF TG_LEVEL <> 'ROW' THEN
    RAISE EXCEPTION 'add_history_rows() may only be used as a row-level trigger';
  END IF;

  _operation := TG_OP::text;

  -- Detect the type of operation, the changed fields and the ID of the changed record.
  IF (_operation = 'UPDATE') THEN
    _changed_fields := (hstore(NEW.*) - hstore(OLD.*));
    IF _changed_fields = hstore('') THEN
      -- There are no changes to record in the history table.
      RETURN NULL;
    END IF;
    _old_data := hstore(OLD.*);
    _new_data := hstore(NEW.*);
    _record_id := OLD.id;
  ELSIF (_operation = 'DELETE') THEN
    _changed_fields := hstore(OLD.*);
    _old_data := _changed_fields;
    _record_id := OLD.id;
  ELSIF (_operation = 'INSERT') THEN
    _changed_fields := hstore(NEW.*);
    _new_data := _changed_fields;
    _record_id := NEW.id;
  ELSE
    RAISE EXCEPTION 'add_history_rows() supports only INSERT, UPDATE and DELETE';
  END IF;

  -- Detect the name of the user if present.
  BEGIN
    _user_name := current_setting('xroad.user_name');
  EXCEPTION WHEN undefined_object THEN
    _user_name := session_user::text;
  END;

  -- Fill and insert a history record for each changed field.
  FOR _field_data IN SELECT kv."key", kv."value" FROM each(_changed_fields) kv
  LOOP
    PERFORM insert_history_row(
      _user_name, _operation, TG_TABLE_NAME::text,
    _field_data, _old_data, _new_data, _record_id);
  END LOOP;

  RETURN NULL;
END;
$body$
LANGUAGE 'plpgsql';
      SQL

      # Create triggers on all the tables that need history support.
      for table_name in TABLES_WITH_HISTORY_SUPPORT
        execute <<-SQL
DROP TRIGGER IF EXISTS update_history ON #{table_name};
CREATE TRIGGER update_history AFTER INSERT OR UPDATE OR DELETE ON #{table_name}
    FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        SQL
      end
    end
  end

  def down
    # First drop the history related triggers on all the relevant tables if
    # we are using Postgres.
    adapter_name = ActiveRecord::Base.connection.adapter_name
    if adapter_name == "PostgreSQL"
      for table_name in TABLES_WITH_HISTORY_SUPPORT
        execute <<-SQL
DROP TRIGGER IF EXISTS update_history ON #{table_name};
        SQL
      end
      execute <<-SQL
DROP FUNCTION IF EXISTS add_history_rows();
DROP FUNCTION IF EXISTS insert_history_row(
    user_name text, operation text, table_name text,
    field_data changed_field_type, old_data hstore, new_data hstore, record_id integer);
DROP TYPE changed_field_type;
DROP TABLE history;
      SQL
    end

  end
end
