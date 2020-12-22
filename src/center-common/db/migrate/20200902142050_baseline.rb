class Baseline < ActiveRecord::Migration
  def up
    if ActiveRecord::Migrator.current_version == 0
      # creates the database from scratch
      # only run this migration on an empty (new) database
      up_baseline
    else
      up_update_fix_sequences
    end
  end

  def up_update_fix_sequences
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      schema = ActiveRecord::Base.connection_config[:schema] || 'centerui'

      suppress_messages do
        execute <<-SQL
        CREATE OR REPLACE FUNCTION _fix_sequence_all(schema_name text) RETURNS void
            LANGUAGE plpgsql
            AS $$
        DECLARE
          s INTEGER;
          t INTEGER;
          x RECORD;
        BEGIN
          FOR x IN SELECT PGT.schemaname,S.relname sname ,C.attname, T.relname tname
           FROM pg_class AS S, pg_depend AS D, pg_class AS T, pg_attribute AS C, pg_tables AS PGT
           WHERE S.relkind = 'S' AND S.oid = D.objid AND D.refobjid = T.oid AND D.refobjid = C.attrelid AND D.refobjsubid = C.attnum AND T.relname = PGT.tablename AND PGT.schemaname = schema_name
           LOOP
             -- get max used value from table
             EXECUTE format('select COALESCE(max(%I),0) from %I.%I', x.attname, x.schemaname, x.tname) into t;
               LOOP
                 -- roll sequence till it is bigger than used value
                 EXECUTE format('select nextval(''%I.%I'')', x.schemaname, x.sname) into s;
                 IF s>t THEN
                   exit;
                 END IF;
               END LOOP;
           END LOOP;
        END;
        $$;

        CREATE OR REPLACE FUNCTION fix_sequence(schema_name text)
          RETURNS void
          LANGUAGE plpgsql
          AS $$
        BEGIN
          IF exists(SELECT 1 FROM pg_extension WHERE extname='bdr') THEN
            RAISE NOTICE 'BDR';
            PERFORM bdr.bdr_replicate_ddl_command(format('select %I._fix_sequence_all(%L);', schema_name, schema_name));
          ELSE
            RAISE NOTICE 'nonBDR';
            PERFORM _fix_sequence_all(schema_name);
          END IF;
        END;
        $$;

        DROP FUNCTION IF EXISTS fix_sequence();
        DROP FUNCTION IF EXISTS _fix_sequence_all();
        SQL
      end
    end
  end

  def up_baseline

    suppress_messages do
      create_table "anchor_url_certs", :force => true do |t|
        t.integer "anchor_url_id"
        t.binary "cert"
      end

      add_index "anchor_url_certs", ["anchor_url_id"], :name => "index_anchor_url_certs_on_anchor_url_id"

      create_table "anchor_urls", :force => true do |t|
        t.integer "trusted_anchor_id"
        t.string "url"
      end

      add_index "anchor_urls", ["trusted_anchor_id"], :name => "index_anchor_urls_on_trusted_anchor_id"

      create_table "approved_cas", :force => true do |t|
        t.string "name"
        t.boolean "authentication_only"
        t.string "identifier_decoder_member_class"
        t.string "identifier_decoder_method_name"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
        t.integer "top_ca_id"
        t.string "cert_profile_info"
      end

      add_index "approved_cas", ["top_ca_id"], :name => "index_approved_cas_on_top_ca_id"

      create_table "approved_tsas", :force => true do |t|
        t.string "name"
        t.string "url"
        t.binary "cert"
        t.datetime "valid_from"
        t.datetime "valid_to"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      create_table "auth_certs", :force => true do |t|
        t.integer "security_server_id"
        t.binary "cert"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "auth_certs", ["security_server_id"], :name => "index_auth_certs_on_security_server_id"

      create_table "ca_infos", :force => true do |t|
        t.binary "cert"
        t.integer "intermediate_ca_id"
        t.datetime "valid_from"
        t.datetime "valid_to"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "ca_infos", ["intermediate_ca_id"], :name => "index_ca_infos_on_intermediate_ca_id"

      create_table "central_services", :force => true do |t|
        t.string "service_code"
        t.integer "target_service_id"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "central_services", ["target_service_id"], :name => "index_central_services_on_target_service_id"

      create_table "configuration_signing_keys", :force => true do |t|
        t.integer "configuration_source_id"
        t.string "key_identifier"
        t.binary "cert"
        t.datetime "key_generated_at"
        t.string "token_identifier"
      end

      add_index "configuration_signing_keys", ["configuration_source_id"], :name => "index_configuration_signing_keys_on_configuration_source_id"

      create_table "configuration_sources", :force => true do |t|
        t.string "source_type"
        t.integer "active_key_id"
        t.binary "anchor_file"
        t.text "anchor_file_hash"
        t.datetime "anchor_generated_at"
        t.string "ha_node_name"
      end

      add_index "configuration_sources", ["active_key_id"], :name => "index_configuration_sources_on_active_key_id"
      add_index "configuration_sources", ["source_type", "ha_node_name"], :name => "unique_type", :unique => true

      create_table "distributed_files", :force => true do |t|
        t.string "file_name"
        t.binary "file_data"
        t.string "content_identifier"
        t.datetime "file_updated_at"
        t.string "ha_node_name"
        t.integer "version", :default => 0, :null => false
      end

      add_index "distributed_files", ["content_identifier", "version", "ha_node_name"], :name => "unique_content_identifier_version_ha_node_name", :unique => true

      create_table "global_group_members", :force => true do |t|
        t.integer "group_member_id"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
        t.integer "global_group_id"
      end

      add_index "global_group_members", ["global_group_id"], :name => "index_global_group_members_on_global_group_id"
      add_index "global_group_members", ["group_member_id"], :name => "index_global_group_members_on_group_member_id"

      create_table "global_groups", :force => true do |t|
        t.string "group_code"
        t.string "description"
        t.integer "member_count"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      create_table "history", :force => true do |t|
        t.string "operation", :null => false
        t.string "table_name", :null => false
        t.integer "record_id", :null => false
        t.string "field_name", :null => false
        t.text "old_value"
        t.text "new_value"
        t.string "user_name", :null => false
        t.datetime "timestamp", :null => false
        t.string "ha_node_name"
      end

      create_table "identifiers", :force => true do |t|
        t.string "object_type"
        t.string "xroad_instance"
        t.string "member_class"
        t.string "member_code"
        t.string "subsystem_code"
        t.string "service_code"
        t.string "server_code"
        t.string "type"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
        t.string "service_version"
      end

      create_table "member_classes", :force => true do |t|
        t.string "code"
        t.string "description"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      create_table "ocsp_infos", :force => true do |t|
        t.string "url"
        t.binary "cert"
        t.integer "ca_info_id"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "ocsp_infos", ["ca_info_id"], :name => "index_ocsp_infos_on_ca_info_id"

      create_table "request_processings", :force => true do |t|
        t.string "type"
        t.string "status"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      create_table "requests", :force => true do |t|
        t.integer "request_processing_id"
        t.string "type"
        t.integer "security_server_id"
        t.integer "sec_serv_user_id"
        t.binary "auth_cert"
        t.string "address"
        t.string "origin"
        t.string "server_owner_name"
        t.string "server_user_name"
        t.text "comments"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
        t.string "server_owner_class"
        t.string "server_owner_code"
        t.string "server_code"
        t.string "processing_status"
      end

      add_index "requests", ["request_processing_id"], :name => "index_requests_on_request_processing_id"
      add_index "requests", ["sec_serv_user_id"], :name => "index_requests_on_sec_serv_user_id"
      add_index "requests", ["security_server_id"], :name => "index_requests_on_security_server_id"

      create_table "security_categories", :force => true do |t|
        t.string "code"
        t.string "description"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      create_table "security_server_client_names", :force => true do |t|
        t.string "name"
        t.integer "client_identifier_id"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "security_server_client_names", ["client_identifier_id"], :name => "index_security_server_client_names_on_client_identifier_id"

      create_table "security_server_clients", :force => true do |t|
        t.string "member_code"
        t.string "subsystem_code"
        t.string "name"
        t.integer "xroad_member_id"
        t.integer "member_class_id"
        t.integer "server_client_id"
        t.string "type"
        t.string "administrative_contact"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "security_server_clients", ["member_class_id"], :name => "index_security_server_clients_on_member_class_id"
      add_index "security_server_clients", ["server_client_id"], :name => "index_security_server_clients_on_server_client_id"
      add_index "security_server_clients", ["xroad_member_id"], :name => "index_security_server_clients_on_xroad_member_id"

      create_table "security_servers", :force => true do |t|
        t.string "server_code"
        t.integer "owner_id"
        t.string "address"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_index "security_servers", ["owner_id"], :name => "index_security_servers_on_xroad_member_id"

      create_table "security_servers_security_categories", :force => true do |t|
        t.integer "security_server_id", :null => false
        t.integer "security_category_id", :null => false
      end

      add_index "security_servers_security_categories", ["security_category_id"], :name => "index_server_to_category"
      add_index "security_servers_security_categories", ["security_server_id"], :name => "index_server_category_to_server_id"

      create_table "server_clients", :force => true do |t|
        t.integer "security_server_id", :null => false
        t.integer "security_server_client_id", :null => false
      end

      add_index "server_clients", ["security_server_client_id"], :name => "index_server_clients_on_security_server_client_id"
      add_index "server_clients", ["security_server_id"], :name => "index_server_clients_on_security_server_id"

      create_table "system_parameters", :force => true do |t|
        t.string "key"
        t.string "value"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
        t.string "ha_node_name"
      end

      create_table "trusted_anchors", :force => true do |t|
        t.string "instance_identifier"
        t.binary "trusted_anchor_file"
        t.text "trusted_anchor_hash"
        t.datetime "created_at"
        t.datetime "updated_at"
        t.datetime "generated_at"
      end

      create_table "ui_users", :force => true do |t|
        t.string "username"
        t.string "locale"
        t.datetime "created_at", :null => false
        t.datetime "updated_at", :null => false
      end

      add_foreign_key "anchor_url_certs", "anchor_urls", name: "anchor_url_certs_anchor_url_id_fk", dependent: :delete
      add_foreign_key "anchor_urls", "trusted_anchors", name: "anchor_urls_trusted_anchor_id_fk", dependent: :delete
      add_foreign_key "approved_cas", "ca_infos", name: "approved_cas_top_ca_id_fk", column: "top_ca_id", dependent: :delete
      add_foreign_key "auth_certs", "security_servers", name: "auth_certs_security_server_id_fk", dependent: :delete
      add_foreign_key "ca_infos", "approved_cas", name: "ca_infos_intermediate_ca_id_fk", column: "intermediate_ca_id", dependent: :delete
      add_foreign_key "central_services", "identifiers", name: "central_services_target_service_id_fk", column: "target_service_id", dependent: :nullify
      add_foreign_key "configuration_signing_keys", "configuration_sources", name: "configuration_signing_keys_configuration_source_id_fk", dependent: :delete
      add_foreign_key "configuration_sources", "configuration_signing_keys", name: "configuration_sources_active_key_id_fk", column: "active_key_id", dependent: :nullify
      add_foreign_key "global_group_members", "global_groups", name: "global_group_members_global_group_id_fk", dependent: :delete
      add_foreign_key "global_group_members", "identifiers", name: "global_group_members_group_member_id_fk", column: "group_member_id", dependent: :delete
      add_foreign_key "ocsp_infos", "ca_infos", name: "ocsp_infos_ca_info_id_fk", dependent: :delete
      add_foreign_key "requests", "identifiers", name: "requests_sec_serv_user_id_fk", column: "sec_serv_user_id", dependent: :delete
      add_foreign_key "requests", "identifiers", name: "requests_security_server_id_fk", column: "security_server_id", dependent: :delete
      add_foreign_key "requests", "request_processings", name: "requests_request_processing_id_fk", dependent: :delete
      add_foreign_key "security_server_client_names", "identifiers", name: "security_server_client_names_client_identifier_id_fk", column: "client_identifier_id", dependent: :delete
      add_foreign_key "security_server_clients", "identifiers", name: "security_server_clients_server_client_id_fk", column: "server_client_id", dependent: :delete
      add_foreign_key "security_server_clients", "member_classes", name: "security_server_clients_member_class_id_fk", dependent: :delete
      add_foreign_key "security_server_clients", "security_server_clients", name: "security_server_clients_xroad_member_id_fk", column: "xroad_member_id", dependent: :delete
      add_foreign_key "security_servers", "security_server_clients", name: "security_servers_owner_id_fk", column: "owner_id", dependent: :delete
      add_foreign_key "security_servers_security_categories", "security_categories", name: "security_servers_security_categories_security_category_id_fk", dependent: :delete
      add_foreign_key "security_servers_security_categories", "security_servers", name: "security_servers_security_categories_security_server_id_fk", dependent: :delete
      add_foreign_key "server_clients", "security_server_clients", name: "server_clients_security_server_client_id_fk", dependent: :delete
      add_foreign_key "server_clients", "security_servers", name: "server_clients_security_server_id_fk", dependent: :delete
    end

    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      schema = ActiveRecord::Base.connection_config[:schema] || 'centerui'

      suppress_messages do
        execute <<-SQL
        CREATE TYPE changed_field_type AS (
          field_key text,
          field_value text
        );

        CREATE FUNCTION _fix_sequence_all(schema_name text) RETURNS void
            LANGUAGE plpgsql
            AS $$
        DECLARE
          s INTEGER;
          t INTEGER;
          x RECORD;
        BEGIN
          FOR x IN SELECT PGT.schemaname,S.relname sname ,C.attname, T.relname tname
           FROM pg_class AS S, pg_depend AS D, pg_class AS T, pg_attribute AS C, pg_tables AS PGT
           WHERE S.relkind = 'S' AND S.oid = D.objid AND D.refobjid = T.oid AND D.refobjid = C.attrelid AND D.refobjsubid = C.attnum AND T.relname = PGT.tablename AND PGT.schemaname = schema_name
           LOOP
             -- get max used value from table
             EXECUTE format('select COALESCE(max(%I),0) from %I.%I', x.attname, x.schemaname, x.tname) into t;
               LOOP
                 -- roll sequence till it is bigger than used value
                 EXECUTE format('select nextval(''%I.%I'')', x.schemaname, x.sname) into s;
                 IF s>t THEN
                   exit;
                 END IF;
               END LOOP;
           END LOOP;
        END;
        $$;

        CREATE FUNCTION fix_sequence(schema_name text)
          RETURNS void
          LANGUAGE plpgsql
          AS $$
        BEGIN
          IF exists(SELECT 1 FROM pg_extension WHERE extname='bdr') THEN
            RAISE NOTICE 'BDR';
            PERFORM bdr.bdr_replicate_ddl_command(format('select %I._fix_sequence_all(%L);', schema_name, schema_name));
          ELSE
            RAISE NOTICE 'nonBDR';
            PERFORM _fix_sequence_all(schema_name);
          END IF;
        END;
        $$;

        CREATE FUNCTION add_history_rows() RETURNS trigger
            LANGUAGE plpgsql
            AS $$
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
        $$;

        CREATE FUNCTION insert_history_row(user_name text, operation text, table_name text, field_data changed_field_type, old_data hstore, new_data hstore, record_id integer) RETURNS void
            LANGUAGE plpgsql
            AS $$
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
        $$;

        CREATE FUNCTION insert_node_name() RETURNS trigger
            LANGUAGE plpgsql
            AS $$
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
        $$;

        CREATE VIEW ha_cluster_status AS
         SELECT sp.ha_node_name,
            sp.value AS address,
            df.configuration_generated
           FROM (system_parameters sp
             LEFT JOIN ( SELECT distributed_files.ha_node_name,
                    max(distributed_files.file_updated_at) AS configuration_generated
                   FROM distributed_files
                  WHERE ((distributed_files.content_identifier)::text = 'PRIVATE-PARAMETERS'::text)
                  GROUP BY distributed_files.ha_node_name) df ON (((sp.ha_node_name)::text = (df.ha_node_name)::text)))
          WHERE ((sp.key)::text = 'centralServerAddress'::text);

        CREATE TRIGGER insert_node_name BEFORE INSERT ON configuration_sources FOR EACH ROW EXECUTE PROCEDURE insert_node_name();
        CREATE TRIGGER insert_node_name BEFORE INSERT ON distributed_files FOR EACH ROW EXECUTE PROCEDURE insert_node_name();
        CREATE TRIGGER insert_node_name BEFORE INSERT ON history FOR EACH ROW EXECUTE PROCEDURE insert_node_name();
        CREATE TRIGGER insert_node_name BEFORE INSERT ON system_parameters FOR EACH ROW EXECUTE PROCEDURE insert_node_name();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON anchor_url_certs FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON anchor_urls FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON approved_cas FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON approved_tsas FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON auth_certs FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON ca_infos FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON central_services FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON configuration_signing_keys FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON configuration_sources FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON global_group_members FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON identifiers FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON member_classes FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON ocsp_infos FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON request_processings FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON requests FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_categories FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_server_client_names FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_server_clients FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_servers FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_servers_security_categories FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON server_clients FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON trusted_anchors FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON ui_users FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON global_groups FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON system_parameters FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
        SQL
      end
    end
  end
end
