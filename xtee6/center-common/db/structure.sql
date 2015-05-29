--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: hstore; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;


--
-- Name: EXTENSION hstore; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION hstore IS 'data type for storing sets of (key, value) pairs';


SET search_path = public, pg_catalog;

--
-- Name: changed_field_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE changed_field_type AS (
	field_key text,
	field_value text
);


--
-- Name: add_history_rows(); Type: FUNCTION; Schema: public; Owner: -
--

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


--
-- Name: insert_history_row(text, text, text, changed_field_type, hstore, hstore, integer); Type: FUNCTION; Schema: public; Owner: -
--

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


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: anchor_url_certs; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE anchor_url_certs (
    id integer NOT NULL,
    anchor_url_id integer,
    certificate bytea
);


--
-- Name: anchor_url_certs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE anchor_url_certs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: anchor_url_certs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE anchor_url_certs_id_seq OWNED BY anchor_url_certs.id;


--
-- Name: anchor_urls; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE anchor_urls (
    id integer NOT NULL,
    trusted_anchor_id integer,
    url character varying(255)
);


--
-- Name: anchor_urls_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE anchor_urls_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: anchor_urls_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE anchor_urls_id_seq OWNED BY anchor_urls.id;


--
-- Name: approved_cas; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE approved_cas (
    id integer NOT NULL,
    name character varying(255),
    authentication_only boolean,
    identifier_decoder_member_class character varying(255),
    identifier_decoder_method_name character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: approved_cas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE approved_cas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approved_cas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE approved_cas_id_seq OWNED BY approved_cas.id;


--
-- Name: approved_tsas; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE approved_tsas (
    id integer NOT NULL,
    name character varying(255),
    url character varying(255),
    cert bytea,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: approved_tsas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE approved_tsas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approved_tsas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE approved_tsas_id_seq OWNED BY approved_tsas.id;


--
-- Name: auth_certs; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE auth_certs (
    id integer NOT NULL,
    security_server_id integer,
    certificate bytea,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: auth_certs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE auth_certs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auth_certs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE auth_certs_id_seq OWNED BY auth_certs.id;


--
-- Name: ca_infos; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE ca_infos (
    id integer NOT NULL,
    cert bytea,
    top_ca_id integer,
    intermediate_ca_id integer,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: ca_infos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE ca_infos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ca_infos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE ca_infos_id_seq OWNED BY ca_infos.id;


--
-- Name: central_services; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE central_services (
    id integer NOT NULL,
    service_code character varying(255),
    target_service_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: central_services_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE central_services_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: central_services_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE central_services_id_seq OWNED BY central_services.id;


--
-- Name: configuration_signing_keys; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE configuration_signing_keys (
    id integer NOT NULL,
    configuration_source_id integer,
    key_identifier character varying(255),
    certificate bytea,
    key_generated_at timestamp without time zone,
    token_identifier character varying(255)
);


--
-- Name: configuration_signing_keys_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE configuration_signing_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: configuration_signing_keys_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE configuration_signing_keys_id_seq OWNED BY configuration_signing_keys.id;


--
-- Name: configuration_sources; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE configuration_sources (
    id integer NOT NULL,
    source_type character varying(255),
    active_key_id integer,
    anchor_file bytea,
    anchor_file_hash text,
    anchor_generated_at timestamp without time zone
);


--
-- Name: configuration_sources_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE configuration_sources_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: configuration_sources_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE configuration_sources_id_seq OWNED BY configuration_sources.id;


--
-- Name: distributed_files; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE distributed_files (
    id integer NOT NULL,
    file_name character varying(255),
    file_data bytea,
    content_identifier character varying(255),
    file_updated_at timestamp without time zone
);


--
-- Name: distributed_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE distributed_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: distributed_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE distributed_files_id_seq OWNED BY distributed_files.id;


--
-- Name: distributed_signed_files; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE distributed_signed_files (
    id integer NOT NULL,
    data bytea,
    data_boundary character varying(255),
    signature bytea,
    sig_algo_id character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: distributed_signed_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE distributed_signed_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: distributed_signed_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE distributed_signed_files_id_seq OWNED BY distributed_signed_files.id;


--
-- Name: global_group_members; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE global_group_members (
    id integer NOT NULL,
    group_member_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    global_group_id integer
);


--
-- Name: global_group_members_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE global_group_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: global_group_members_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE global_group_members_id_seq OWNED BY global_group_members.id;


--
-- Name: global_groups; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE global_groups (
    id integer NOT NULL,
    group_code character varying(255),
    description character varying(255),
    member_count integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: global_groups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE global_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: global_groups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE global_groups_id_seq OWNED BY global_groups.id;


--
-- Name: history; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE history (
    id integer NOT NULL,
    operation character varying(255) NOT NULL,
    table_name character varying(255) NOT NULL,
    record_id integer NOT NULL,
    field_name character varying(255) NOT NULL,
    old_value text,
    new_value text,
    user_name character varying(255) NOT NULL,
    "timestamp" timestamp without time zone NOT NULL
);


--
-- Name: history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE history_id_seq OWNED BY history.id;


--
-- Name: identifiers; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE identifiers (
    id integer NOT NULL,
    object_type character varying(255),
    xroad_instance character varying(255),
    member_class character varying(255),
    member_code character varying(255),
    subsystem_code character varying(255),
    service_code character varying(255),
    server_code character varying(255),
    type character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    service_version character varying(255)
);


--
-- Name: identifiers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE identifiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: identifiers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE identifiers_id_seq OWNED BY identifiers.id;


--
-- Name: member_classes; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE member_classes (
    id integer NOT NULL,
    code character varying(255),
    description character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: member_classes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE member_classes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: member_classes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE member_classes_id_seq OWNED BY member_classes.id;


--
-- Name: ocsp_infos; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE ocsp_infos (
    id integer NOT NULL,
    url character varying(255),
    cert bytea,
    ca_info_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: ocsp_infos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE ocsp_infos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ocsp_infos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE ocsp_infos_id_seq OWNED BY ocsp_infos.id;


--
-- Name: request_processings; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE request_processings (
    id integer NOT NULL,
    type character varying(255),
    status character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: request_processings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE request_processings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: request_processings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE request_processings_id_seq OWNED BY request_processings.id;


--
-- Name: requests; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE requests (
    id integer NOT NULL,
    request_processing_id integer,
    type character varying(255),
    security_server_id integer,
    sec_serv_user_id integer,
    auth_cert bytea,
    address character varying(255),
    origin character varying(255),
    server_owner_name character varying(255),
    server_user_name character varying(255),
    comments text,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    server_owner_class character varying(255),
    server_owner_code character varying(255),
    server_code character varying(255),
    processing_status character varying(255)
);


--
-- Name: requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE requests_id_seq OWNED BY requests.id;


--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE schema_migrations (
    version character varying(255) NOT NULL
);


--
-- Name: security_categories; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE security_categories (
    id integer NOT NULL,
    code character varying(255),
    description character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: security_categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE security_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE security_categories_id_seq OWNED BY security_categories.id;


--
-- Name: security_server_client_names; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE security_server_client_names (
    id integer NOT NULL,
    name character varying(255),
    client_identifier_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: security_server_client_names_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE security_server_client_names_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_server_client_names_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE security_server_client_names_id_seq OWNED BY security_server_client_names.id;


--
-- Name: security_server_clients; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE security_server_clients (
    id integer NOT NULL,
    member_code character varying(255),
    subsystem_code character varying(255),
    name character varying(255),
    xroad_member_id integer,
    member_class_id integer,
    server_client_id integer,
    type character varying(255),
    administrative_contact character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: security_server_clients_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE security_server_clients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_server_clients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE security_server_clients_id_seq OWNED BY security_server_clients.id;


--
-- Name: security_servers; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE security_servers (
    id integer NOT NULL,
    server_code character varying(255),
    xroad_member_id integer,
    address character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: security_servers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE security_servers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_servers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE security_servers_id_seq OWNED BY security_servers.id;


--
-- Name: security_servers_security_categories; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE security_servers_security_categories (
    security_server_id integer NOT NULL,
    security_category_id integer NOT NULL,
    id integer NOT NULL
);


--
-- Name: security_servers_security_categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE security_servers_security_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_servers_security_categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE security_servers_security_categories_id_seq OWNED BY security_servers_security_categories.id;


--
-- Name: server_clients; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE server_clients (
    security_server_id integer NOT NULL,
    security_server_client_id integer NOT NULL,
    id integer NOT NULL
);


--
-- Name: server_clients_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE server_clients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: server_clients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE server_clients_id_seq OWNED BY server_clients.id;


--
-- Name: system_parameters; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE system_parameters (
    id integer NOT NULL,
    key character varying(255),
    value character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: system_parameters_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE system_parameters_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_parameters_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE system_parameters_id_seq OWNED BY system_parameters.id;


--
-- Name: trusted_anchors; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE trusted_anchors (
    id integer NOT NULL,
    instance_identifier character varying(255),
    trusted_anchor_file bytea,
    trusted_anchor_hash text,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    generated_at timestamp without time zone
);


--
-- Name: trusted_anchors_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE trusted_anchors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trusted_anchors_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE trusted_anchors_id_seq OWNED BY trusted_anchors.id;


--
-- Name: ui_users; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE ui_users (
    id integer NOT NULL,
    username character varying(255),
    locale character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: ui_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE ui_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ui_users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE ui_users_id_seq OWNED BY ui_users.id;


--
-- Name: v5_imports; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE v5_imports (
    id integer NOT NULL,
    file_name character varying(255),
    console_output text,
    created_at timestamp without time zone NOT NULL
);


--
-- Name: v5_imports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE v5_imports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: v5_imports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE v5_imports_id_seq OWNED BY v5_imports.id;


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY anchor_url_certs ALTER COLUMN id SET DEFAULT nextval('anchor_url_certs_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY anchor_urls ALTER COLUMN id SET DEFAULT nextval('anchor_urls_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY approved_cas ALTER COLUMN id SET DEFAULT nextval('approved_cas_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY approved_tsas ALTER COLUMN id SET DEFAULT nextval('approved_tsas_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY auth_certs ALTER COLUMN id SET DEFAULT nextval('auth_certs_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY ca_infos ALTER COLUMN id SET DEFAULT nextval('ca_infos_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY central_services ALTER COLUMN id SET DEFAULT nextval('central_services_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY configuration_signing_keys ALTER COLUMN id SET DEFAULT nextval('configuration_signing_keys_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY configuration_sources ALTER COLUMN id SET DEFAULT nextval('configuration_sources_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY distributed_files ALTER COLUMN id SET DEFAULT nextval('distributed_files_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY distributed_signed_files ALTER COLUMN id SET DEFAULT nextval('distributed_signed_files_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_group_members ALTER COLUMN id SET DEFAULT nextval('global_group_members_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_groups ALTER COLUMN id SET DEFAULT nextval('global_groups_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY history ALTER COLUMN id SET DEFAULT nextval('history_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY identifiers ALTER COLUMN id SET DEFAULT nextval('identifiers_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY member_classes ALTER COLUMN id SET DEFAULT nextval('member_classes_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY ocsp_infos ALTER COLUMN id SET DEFAULT nextval('ocsp_infos_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY request_processings ALTER COLUMN id SET DEFAULT nextval('request_processings_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY requests ALTER COLUMN id SET DEFAULT nextval('requests_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_categories ALTER COLUMN id SET DEFAULT nextval('security_categories_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_server_client_names ALTER COLUMN id SET DEFAULT nextval('security_server_client_names_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_server_clients ALTER COLUMN id SET DEFAULT nextval('security_server_clients_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_servers ALTER COLUMN id SET DEFAULT nextval('security_servers_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_servers_security_categories ALTER COLUMN id SET DEFAULT nextval('security_servers_security_categories_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY server_clients ALTER COLUMN id SET DEFAULT nextval('server_clients_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY system_parameters ALTER COLUMN id SET DEFAULT nextval('system_parameters_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY trusted_anchors ALTER COLUMN id SET DEFAULT nextval('trusted_anchors_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_users ALTER COLUMN id SET DEFAULT nextval('ui_users_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY v5_imports ALTER COLUMN id SET DEFAULT nextval('v5_imports_id_seq'::regclass);


--
-- Name: anchor_url_certs_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY anchor_url_certs
    ADD CONSTRAINT anchor_url_certs_pkey PRIMARY KEY (id);


--
-- Name: anchor_urls_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY anchor_urls
    ADD CONSTRAINT anchor_urls_pkey PRIMARY KEY (id);


--
-- Name: approved_cas_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY approved_cas
    ADD CONSTRAINT approved_cas_pkey PRIMARY KEY (id);


--
-- Name: approved_tsas_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY approved_tsas
    ADD CONSTRAINT approved_tsas_pkey PRIMARY KEY (id);


--
-- Name: auth_certs_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY auth_certs
    ADD CONSTRAINT auth_certs_pkey PRIMARY KEY (id);


--
-- Name: ca_infos_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY ca_infos
    ADD CONSTRAINT ca_infos_pkey PRIMARY KEY (id);


--
-- Name: central_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY central_services
    ADD CONSTRAINT central_services_pkey PRIMARY KEY (id);


--
-- Name: configuration_signing_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY configuration_signing_keys
    ADD CONSTRAINT configuration_signing_keys_pkey PRIMARY KEY (id);


--
-- Name: configuration_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY configuration_sources
    ADD CONSTRAINT configuration_sources_pkey PRIMARY KEY (id);


--
-- Name: distributed_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY distributed_files
    ADD CONSTRAINT distributed_files_pkey PRIMARY KEY (id);


--
-- Name: distributed_signed_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY distributed_signed_files
    ADD CONSTRAINT distributed_signed_files_pkey PRIMARY KEY (id);


--
-- Name: global_group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY global_group_members
    ADD CONSTRAINT global_group_members_pkey PRIMARY KEY (id);


--
-- Name: global_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY global_groups
    ADD CONSTRAINT global_groups_pkey PRIMARY KEY (id);


--
-- Name: history_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY history
    ADD CONSTRAINT history_pkey PRIMARY KEY (id);


--
-- Name: identifiers_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY identifiers
    ADD CONSTRAINT identifiers_pkey PRIMARY KEY (id);


--
-- Name: member_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY member_classes
    ADD CONSTRAINT member_classes_pkey PRIMARY KEY (id);


--
-- Name: ocsp_infos_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY ocsp_infos
    ADD CONSTRAINT ocsp_infos_pkey PRIMARY KEY (id);


--
-- Name: request_processings_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY request_processings
    ADD CONSTRAINT request_processings_pkey PRIMARY KEY (id);


--
-- Name: requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY requests
    ADD CONSTRAINT requests_pkey PRIMARY KEY (id);


--
-- Name: security_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY security_categories
    ADD CONSTRAINT security_categories_pkey PRIMARY KEY (id);


--
-- Name: security_server_client_names_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY security_server_client_names
    ADD CONSTRAINT security_server_client_names_pkey PRIMARY KEY (id);


--
-- Name: security_server_clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY security_server_clients
    ADD CONSTRAINT security_server_clients_pkey PRIMARY KEY (id);


--
-- Name: security_servers_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY security_servers
    ADD CONSTRAINT security_servers_pkey PRIMARY KEY (id);


--
-- Name: security_servers_security_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY security_servers_security_categories
    ADD CONSTRAINT security_servers_security_categories_pkey PRIMARY KEY (id);


--
-- Name: server_clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY server_clients
    ADD CONSTRAINT server_clients_pkey PRIMARY KEY (id);


--
-- Name: system_parameters_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY system_parameters
    ADD CONSTRAINT system_parameters_pkey PRIMARY KEY (id);


--
-- Name: trusted_anchors_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY trusted_anchors
    ADD CONSTRAINT trusted_anchors_pkey PRIMARY KEY (id);


--
-- Name: ui_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY ui_users
    ADD CONSTRAINT ui_users_pkey PRIMARY KEY (id);


--
-- Name: v5_imports_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY v5_imports
    ADD CONSTRAINT v5_imports_pkey PRIMARY KEY (id);


--
-- Name: unique_schema_migrations; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE UNIQUE INDEX unique_schema_migrations ON schema_migrations USING btree (version);


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON anchor_url_certs FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON anchor_urls FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON approved_cas FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON approved_tsas FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON auth_certs FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON ca_infos FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON central_services FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON configuration_signing_keys FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON configuration_sources FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON global_group_members FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON identifiers FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON member_classes FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON ocsp_infos FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON request_processings FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON requests FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_categories FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_server_client_names FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_server_clients FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_servers FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON security_servers_security_categories FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON server_clients FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON trusted_anchors FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON ui_users FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON v5_imports FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON global_groups FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: update_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_history AFTER INSERT OR DELETE OR UPDATE ON system_parameters FOR EACH ROW EXECUTE PROCEDURE add_history_rows();


--
-- Name: anchor_url_certs_anchor_url_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY anchor_url_certs
    ADD CONSTRAINT anchor_url_certs_anchor_url_id_fk FOREIGN KEY (anchor_url_id) REFERENCES anchor_urls(id) ON DELETE CASCADE;


--
-- Name: anchor_urls_trusted_anchor_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY anchor_urls
    ADD CONSTRAINT anchor_urls_trusted_anchor_id_fk FOREIGN KEY (trusted_anchor_id) REFERENCES trusted_anchors(id) ON DELETE CASCADE;


--
-- Name: auth_certs_security_server_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY auth_certs
    ADD CONSTRAINT auth_certs_security_server_id_fk FOREIGN KEY (security_server_id) REFERENCES security_servers(id) ON DELETE CASCADE;


--
-- Name: ca_infos_intermediate_ca_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ca_infos
    ADD CONSTRAINT ca_infos_intermediate_ca_id_fk FOREIGN KEY (intermediate_ca_id) REFERENCES approved_cas(id) ON DELETE CASCADE;


--
-- Name: ca_infos_top_ca_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ca_infos
    ADD CONSTRAINT ca_infos_top_ca_id_fk FOREIGN KEY (top_ca_id) REFERENCES approved_cas(id) ON DELETE CASCADE;


--
-- Name: central_services_target_service_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY central_services
    ADD CONSTRAINT central_services_target_service_id_fk FOREIGN KEY (target_service_id) REFERENCES identifiers(id) ON DELETE SET NULL;


--
-- Name: configuration_signing_keys_configuration_source_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY configuration_signing_keys
    ADD CONSTRAINT configuration_signing_keys_configuration_source_id_fk FOREIGN KEY (configuration_source_id) REFERENCES configuration_sources(id) ON DELETE CASCADE;


--
-- Name: configuration_sources_active_key_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY configuration_sources
    ADD CONSTRAINT configuration_sources_active_key_id_fk FOREIGN KEY (active_key_id) REFERENCES configuration_signing_keys(id) ON DELETE SET NULL;


--
-- Name: global_group_members_global_group_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_group_members
    ADD CONSTRAINT global_group_members_global_group_id_fk FOREIGN KEY (global_group_id) REFERENCES global_groups(id) ON DELETE CASCADE;


--
-- Name: global_group_members_group_member_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_group_members
    ADD CONSTRAINT global_group_members_group_member_id_fk FOREIGN KEY (group_member_id) REFERENCES identifiers(id) ON DELETE CASCADE;


--
-- Name: ocsp_infos_ca_info_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ocsp_infos
    ADD CONSTRAINT ocsp_infos_ca_info_id_fk FOREIGN KEY (ca_info_id) REFERENCES ca_infos(id) ON DELETE CASCADE;


--
-- Name: requests_request_processing_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY requests
    ADD CONSTRAINT requests_request_processing_id_fk FOREIGN KEY (request_processing_id) REFERENCES request_processings(id) ON DELETE CASCADE;


--
-- Name: requests_sec_serv_user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY requests
    ADD CONSTRAINT requests_sec_serv_user_id_fk FOREIGN KEY (sec_serv_user_id) REFERENCES identifiers(id) ON DELETE CASCADE;


--
-- Name: requests_security_server_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY requests
    ADD CONSTRAINT requests_security_server_id_fk FOREIGN KEY (security_server_id) REFERENCES identifiers(id) ON DELETE CASCADE;


--
-- Name: security_server_client_names_client_identifier_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_server_client_names
    ADD CONSTRAINT security_server_client_names_client_identifier_id_fk FOREIGN KEY (client_identifier_id) REFERENCES identifiers(id) ON DELETE CASCADE;


--
-- Name: security_server_clients_member_class_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_server_clients
    ADD CONSTRAINT security_server_clients_member_class_id_fk FOREIGN KEY (member_class_id) REFERENCES member_classes(id) ON DELETE CASCADE;


--
-- Name: security_server_clients_server_client_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_server_clients
    ADD CONSTRAINT security_server_clients_server_client_id_fk FOREIGN KEY (server_client_id) REFERENCES identifiers(id) ON DELETE CASCADE;


--
-- Name: security_server_clients_xroad_member_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_server_clients
    ADD CONSTRAINT security_server_clients_xroad_member_id_fk FOREIGN KEY (xroad_member_id) REFERENCES security_server_clients(id) ON DELETE CASCADE;


--
-- Name: security_servers_security_categories_security_category_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_servers_security_categories
    ADD CONSTRAINT security_servers_security_categories_security_category_id_fk FOREIGN KEY (security_category_id) REFERENCES security_categories(id) ON DELETE CASCADE;


--
-- Name: security_servers_security_categories_security_server_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_servers_security_categories
    ADD CONSTRAINT security_servers_security_categories_security_server_id_fk FOREIGN KEY (security_server_id) REFERENCES security_servers(id) ON DELETE CASCADE;


--
-- Name: security_servers_xroad_member_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_servers
    ADD CONSTRAINT security_servers_xroad_member_id_fk FOREIGN KEY (xroad_member_id) REFERENCES security_server_clients(id) ON DELETE CASCADE;


--
-- Name: server_clients_security_server_client_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY server_clients
    ADD CONSTRAINT server_clients_security_server_client_id_fk FOREIGN KEY (security_server_client_id) REFERENCES security_server_clients(id) ON DELETE CASCADE;


--
-- Name: server_clients_security_server_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY server_clients
    ADD CONSTRAINT server_clients_security_server_id_fk FOREIGN KEY (security_server_id) REFERENCES security_servers(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

SET search_path TO "$user",public;

INSERT INTO schema_migrations (version) VALUES ('20150408102110');

INSERT INTO schema_migrations (version) VALUES ('20150415141552');

INSERT INTO schema_migrations (version) VALUES ('20150424110105');

INSERT INTO schema_migrations (version) VALUES ('20150427172544');

INSERT INTO schema_migrations (version) VALUES ('20150430093538');