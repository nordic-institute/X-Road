--
-- PostgreSQL database dump
--

-- Dumped from database version 9.2.5
-- Dumped by pg_dump version 9.2.5
-- Started on 2013-11-28 15:09:29 EET

SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- TOC entry 217 (class 3079 OID 11767)
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- TOC entry 2193 (class 0 OID 0)
-- Dependencies: 217
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- TOC entry 172 (class 1259 OID 76147)
-- Name: approved_tsps; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE approved_tsps (
    id integer NOT NULL,
    name character varying(255),
    url character varying(255),
    cert bytea,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.approved_tsps OWNER TO centerui;

--
-- TOC entry 171 (class 1259 OID 76145)
-- Name: approved_tsps_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE approved_tsps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.approved_tsps_id_seq OWNER TO centerui;

--
-- TOC entry 2194 (class 0 OID 0)
-- Dependencies: 171
-- Name: approved_tsps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE approved_tsps_id_seq OWNED BY approved_tsps.id;


--
-- TOC entry 174 (class 1259 OID 76158)
-- Name: auth_certs; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE auth_certs (
    id integer NOT NULL,
    security_server_id integer,
    certificate bytea,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.auth_certs OWNER TO centerui;

--
-- TOC entry 173 (class 1259 OID 76156)
-- Name: auth_certs_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE auth_certs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.auth_certs_id_seq OWNER TO centerui;

--
-- TOC entry 2195 (class 0 OID 0)
-- Dependencies: 173
-- Name: auth_certs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE auth_certs_id_seq OWNED BY auth_certs.id;


--
-- TOC entry 176 (class 1259 OID 76169)
-- Name: ca_infos; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
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


ALTER TABLE public.ca_infos OWNER TO centerui;

--
-- TOC entry 175 (class 1259 OID 76167)
-- Name: ca_infos_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE ca_infos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.ca_infos_id_seq OWNER TO centerui;

--
-- TOC entry 2196 (class 0 OID 0)
-- Dependencies: 175
-- Name: ca_infos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE ca_infos_id_seq OWNED BY ca_infos.id;


--
-- TOC entry 178 (class 1259 OID 76180)
-- Name: central_services; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE central_services (
    id integer NOT NULL,
    service_code character varying(255),
    target_service_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.central_services OWNER TO centerui;

--
-- TOC entry 177 (class 1259 OID 76178)
-- Name: central_services_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE central_services_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.central_services_id_seq OWNER TO centerui;

--
-- TOC entry 2197 (class 0 OID 0)
-- Dependencies: 177
-- Name: central_services_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE central_services_id_seq OWNED BY central_services.id;


--
-- TOC entry 214 (class 1259 OID 76358)
-- Name: distributed_files; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE distributed_files (
    id integer NOT NULL,
    file_name character varying(255),
    file_data text
);


ALTER TABLE public.distributed_files OWNER TO centerui;

--
-- TOC entry 213 (class 1259 OID 76356)
-- Name: distributed_files_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE distributed_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.distributed_files_id_seq OWNER TO centerui;

--
-- TOC entry 2198 (class 0 OID 0)
-- Dependencies: 213
-- Name: distributed_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE distributed_files_id_seq OWNED BY distributed_files.id;


--
-- TOC entry 216 (class 1259 OID 76369)
-- Name: distributed_signed_files; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE distributed_signed_files (
    id integer NOT NULL,
    data text,
    data_boundary character varying(255),
    signature text,
    sig_algo_id character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.distributed_signed_files OWNER TO centerui;

--
-- TOC entry 215 (class 1259 OID 76367)
-- Name: distributed_signed_files_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE distributed_signed_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.distributed_signed_files_id_seq OWNER TO centerui;

--
-- TOC entry 2199 (class 0 OID 0)
-- Dependencies: 215
-- Name: distributed_signed_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE distributed_signed_files_id_seq OWNED BY distributed_signed_files.id;


--
-- TOC entry 180 (class 1259 OID 76188)
-- Name: federated_sdsbs; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE federated_sdsbs (
    id integer NOT NULL,
    code character varying(255),
    address character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.federated_sdsbs OWNER TO centerui;

--
-- TOC entry 179 (class 1259 OID 76186)
-- Name: federated_sdsbs_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE federated_sdsbs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.federated_sdsbs_id_seq OWNER TO centerui;

--
-- TOC entry 2200 (class 0 OID 0)
-- Dependencies: 179
-- Name: federated_sdsbs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE federated_sdsbs_id_seq OWNED BY federated_sdsbs.id;


--
-- TOC entry 182 (class 1259 OID 76199)
-- Name: global_group_members; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE global_group_members (
    id integer NOT NULL,
    group_member_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    global_group_id integer
);


ALTER TABLE public.global_group_members OWNER TO centerui;

--
-- TOC entry 181 (class 1259 OID 76197)
-- Name: global_group_members_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE global_group_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.global_group_members_id_seq OWNER TO centerui;

--
-- TOC entry 2201 (class 0 OID 0)
-- Dependencies: 181
-- Name: global_group_members_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE global_group_members_id_seq OWNED BY global_group_members.id;


--
-- TOC entry 184 (class 1259 OID 76207)
-- Name: global_groups; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE global_groups (
    id integer NOT NULL,
    group_code character varying(255),
    description character varying(255),
    member_count integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.global_groups OWNER TO centerui;

--
-- TOC entry 183 (class 1259 OID 76205)
-- Name: global_groups_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE global_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.global_groups_id_seq OWNER TO centerui;

--
-- TOC entry 2202 (class 0 OID 0)
-- Dependencies: 183
-- Name: global_groups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE global_groups_id_seq OWNED BY global_groups.id;


--
-- TOC entry 186 (class 1259 OID 76218)
-- Name: identifiers; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE identifiers (
    id integer NOT NULL,
    object_type character varying(255),
    sdsb_instance character varying(255),
    member_class character varying(255),
    member_code character varying(255),
    subsystem_code character varying(255),
    service_code character varying(255),
    server_code character varying(255),
    type character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.identifiers OWNER TO centerui;

--
-- TOC entry 185 (class 1259 OID 76216)
-- Name: identifiers_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE identifiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.identifiers_id_seq OWNER TO centerui;

--
-- TOC entry 2203 (class 0 OID 0)
-- Dependencies: 185
-- Name: identifiers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE identifiers_id_seq OWNED BY identifiers.id;


--
-- TOC entry 188 (class 1259 OID 76229)
-- Name: member_class_mappings; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE member_class_mappings (
    id integer NOT NULL,
    federated_member_class character varying(255),
    member_class_id integer,
    federated_sdsb_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.member_class_mappings OWNER TO centerui;

--
-- TOC entry 187 (class 1259 OID 76227)
-- Name: member_class_mappings_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE member_class_mappings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.member_class_mappings_id_seq OWNER TO centerui;

--
-- TOC entry 2204 (class 0 OID 0)
-- Dependencies: 187
-- Name: member_class_mappings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE member_class_mappings_id_seq OWNED BY member_class_mappings.id;


--
-- TOC entry 190 (class 1259 OID 76237)
-- Name: member_classes; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE member_classes (
    id integer NOT NULL,
    code character varying(255),
    description character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.member_classes OWNER TO centerui;

--
-- TOC entry 189 (class 1259 OID 76235)
-- Name: member_classes_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE member_classes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.member_classes_id_seq OWNER TO centerui;

--
-- TOC entry 2205 (class 0 OID 0)
-- Dependencies: 189
-- Name: member_classes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE member_classes_id_seq OWNED BY member_classes.id;


--
-- TOC entry 192 (class 1259 OID 76248)
-- Name: ocsp_infos; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE ocsp_infos (
    id integer NOT NULL,
    url character varying(255),
    cert bytea,
    ca_info_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.ocsp_infos OWNER TO centerui;

--
-- TOC entry 191 (class 1259 OID 76246)
-- Name: ocsp_infos_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE ocsp_infos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.ocsp_infos_id_seq OWNER TO centerui;

--
-- TOC entry 2206 (class 0 OID 0)
-- Dependencies: 191
-- Name: ocsp_infos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE ocsp_infos_id_seq OWNED BY ocsp_infos.id;


--
-- TOC entry 194 (class 1259 OID 76259)
-- Name: pkis; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE pkis (
    id integer NOT NULL,
    name character varying(255),
    authentication_only boolean,
    name_extractor_member_class character varying(255),
    name_extractor_method_name character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.pkis OWNER TO centerui;

--
-- TOC entry 193 (class 1259 OID 76257)
-- Name: pkis_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE pkis_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.pkis_id_seq OWNER TO centerui;

--
-- TOC entry 2207 (class 0 OID 0)
-- Dependencies: 193
-- Name: pkis_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE pkis_id_seq OWNED BY pkis.id;


--
-- TOC entry 198 (class 1259 OID 76281)
-- Name: request_processings; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE request_processings (
    id integer NOT NULL,
    type character varying(255),
    status character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.request_processings OWNER TO centerui;

--
-- TOC entry 197 (class 1259 OID 76279)
-- Name: request_processings_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE request_processings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.request_processings_id_seq OWNER TO centerui;

--
-- TOC entry 2208 (class 0 OID 0)
-- Dependencies: 197
-- Name: request_processings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE request_processings_id_seq OWNED BY request_processings.id;


--
-- TOC entry 196 (class 1259 OID 76270)
-- Name: requests; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
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
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.requests OWNER TO centerui;

--
-- TOC entry 195 (class 1259 OID 76268)
-- Name: requests_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.requests_id_seq OWNER TO centerui;

--
-- TOC entry 2209 (class 0 OID 0)
-- Dependencies: 195
-- Name: requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE requests_id_seq OWNED BY requests.id;


--
-- TOC entry 168 (class 1259 OID 16611)
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE schema_migrations (
    version character varying(255) NOT NULL
);


ALTER TABLE public.schema_migrations OWNER TO centerui;

--
-- TOC entry 200 (class 1259 OID 76292)
-- Name: security_categories; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE security_categories (
    id integer NOT NULL,
    code character varying(255),
    description character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.security_categories OWNER TO centerui;

--
-- TOC entry 199 (class 1259 OID 76290)
-- Name: security_categories_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE security_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.security_categories_id_seq OWNER TO centerui;

--
-- TOC entry 2210 (class 0 OID 0)
-- Dependencies: 199
-- Name: security_categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE security_categories_id_seq OWNED BY security_categories.id;


--
-- TOC entry 202 (class 1259 OID 76303)
-- Name: security_category_mappings; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE security_category_mappings (
    id integer NOT NULL,
    security_category_id integer,
    federated_sdsb_id integer,
    federated_category character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.security_category_mappings OWNER TO centerui;

--
-- TOC entry 201 (class 1259 OID 76301)
-- Name: security_category_mappings_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE security_category_mappings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.security_category_mappings_id_seq OWNER TO centerui;

--
-- TOC entry 2211 (class 0 OID 0)
-- Dependencies: 201
-- Name: security_category_mappings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE security_category_mappings_id_seq OWNED BY security_category_mappings.id;


--
-- TOC entry 206 (class 1259 OID 76322)
-- Name: security_server_client_names; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE security_server_client_names (
    id integer NOT NULL,
    name character varying(255),
    client_identifier_id integer,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.security_server_client_names OWNER TO centerui;

--
-- TOC entry 205 (class 1259 OID 76320)
-- Name: security_server_client_names_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE security_server_client_names_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.security_server_client_names_id_seq OWNER TO centerui;

--
-- TOC entry 2212 (class 0 OID 0)
-- Dependencies: 205
-- Name: security_server_client_names_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE security_server_client_names_id_seq OWNED BY security_server_client_names.id;


--
-- TOC entry 204 (class 1259 OID 76311)
-- Name: security_server_clients; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE security_server_clients (
    id integer NOT NULL,
    member_code character varying(255),
    subsystem_code character varying(255),
    name character varying(255),
    sdsb_member_id integer,
    member_class_id integer,
    server_client_id integer,
    type character varying(255),
    administrative_contact character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.security_server_clients OWNER TO centerui;

--
-- TOC entry 203 (class 1259 OID 76309)
-- Name: security_server_clients_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE security_server_clients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.security_server_clients_id_seq OWNER TO centerui;

--
-- TOC entry 2213 (class 0 OID 0)
-- Dependencies: 203
-- Name: security_server_clients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE security_server_clients_id_seq OWNED BY security_server_clients.id;


--
-- TOC entry 208 (class 1259 OID 76330)
-- Name: security_servers; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE security_servers (
    id integer NOT NULL,
    server_code character varying(255),
    sdsb_member_id integer,
    address character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.security_servers OWNER TO centerui;

--
-- TOC entry 207 (class 1259 OID 76328)
-- Name: security_servers_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE security_servers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.security_servers_id_seq OWNER TO centerui;

--
-- TOC entry 2214 (class 0 OID 0)
-- Dependencies: 207
-- Name: security_servers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE security_servers_id_seq OWNED BY security_servers.id;


--
-- TOC entry 209 (class 1259 OID 76339)
-- Name: security_servers_security_categories; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE security_servers_security_categories (
    security_server_id integer NOT NULL,
    security_category_id integer NOT NULL
);


ALTER TABLE public.security_servers_security_categories OWNER TO centerui;

--
-- TOC entry 210 (class 1259 OID 76342)
-- Name: server_clients; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE server_clients (
    security_server_id integer NOT NULL,
    security_server_client_id integer NOT NULL
);


ALTER TABLE public.server_clients OWNER TO centerui;

--
-- TOC entry 170 (class 1259 OID 58733)
-- Name: signed_xmls; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE signed_xmls (
    id integer NOT NULL,
    data text,
    signature text,
    boundary_value character varying(255),
    sig_algo_id character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.signed_xmls OWNER TO centerui;

--
-- TOC entry 169 (class 1259 OID 58731)
-- Name: signed_xmls_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE signed_xmls_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.signed_xmls_id_seq OWNER TO centerui;

--
-- TOC entry 2215 (class 0 OID 0)
-- Dependencies: 169
-- Name: signed_xmls_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE signed_xmls_id_seq OWNED BY signed_xmls.id;


--
-- TOC entry 212 (class 1259 OID 76347)
-- Name: system_parameters; Type: TABLE; Schema: public; Owner: centerui; Tablespace: 
--

CREATE TABLE system_parameters (
    id integer NOT NULL,
    key character varying(255),
    value character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


ALTER TABLE public.system_parameters OWNER TO centerui;

--
-- TOC entry 211 (class 1259 OID 76345)
-- Name: system_parameters_id_seq; Type: SEQUENCE; Schema: public; Owner: centerui
--

CREATE SEQUENCE system_parameters_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.system_parameters_id_seq OWNER TO centerui;

--
-- TOC entry 2216 (class 0 OID 0)
-- Dependencies: 211
-- Name: system_parameters_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: centerui
--

ALTER SEQUENCE system_parameters_id_seq OWNED BY system_parameters.id;


--
-- TOC entry 2011 (class 2604 OID 76150)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY approved_tsps ALTER COLUMN id SET DEFAULT nextval('approved_tsps_id_seq'::regclass);


--
-- TOC entry 2012 (class 2604 OID 76161)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY auth_certs ALTER COLUMN id SET DEFAULT nextval('auth_certs_id_seq'::regclass);


--
-- TOC entry 2013 (class 2604 OID 76172)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY ca_infos ALTER COLUMN id SET DEFAULT nextval('ca_infos_id_seq'::regclass);


--
-- TOC entry 2014 (class 2604 OID 76183)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY central_services ALTER COLUMN id SET DEFAULT nextval('central_services_id_seq'::regclass);


--
-- TOC entry 2031 (class 2604 OID 76361)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY distributed_files ALTER COLUMN id SET DEFAULT nextval('distributed_files_id_seq'::regclass);


--
-- TOC entry 2032 (class 2604 OID 76372)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY distributed_signed_files ALTER COLUMN id SET DEFAULT nextval('distributed_signed_files_id_seq'::regclass);


--
-- TOC entry 2015 (class 2604 OID 76191)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY federated_sdsbs ALTER COLUMN id SET DEFAULT nextval('federated_sdsbs_id_seq'::regclass);


--
-- TOC entry 2016 (class 2604 OID 76202)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY global_group_members ALTER COLUMN id SET DEFAULT nextval('global_group_members_id_seq'::regclass);


--
-- TOC entry 2017 (class 2604 OID 76210)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY global_groups ALTER COLUMN id SET DEFAULT nextval('global_groups_id_seq'::regclass);


--
-- TOC entry 2018 (class 2604 OID 76221)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY identifiers ALTER COLUMN id SET DEFAULT nextval('identifiers_id_seq'::regclass);


--
-- TOC entry 2019 (class 2604 OID 76232)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY member_class_mappings ALTER COLUMN id SET DEFAULT nextval('member_class_mappings_id_seq'::regclass);


--
-- TOC entry 2020 (class 2604 OID 76240)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY member_classes ALTER COLUMN id SET DEFAULT nextval('member_classes_id_seq'::regclass);


--
-- TOC entry 2021 (class 2604 OID 76251)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY ocsp_infos ALTER COLUMN id SET DEFAULT nextval('ocsp_infos_id_seq'::regclass);


--
-- TOC entry 2022 (class 2604 OID 76262)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY pkis ALTER COLUMN id SET DEFAULT nextval('pkis_id_seq'::regclass);


--
-- TOC entry 2024 (class 2604 OID 76284)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY request_processings ALTER COLUMN id SET DEFAULT nextval('request_processings_id_seq'::regclass);


--
-- TOC entry 2023 (class 2604 OID 76273)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY requests ALTER COLUMN id SET DEFAULT nextval('requests_id_seq'::regclass);


--
-- TOC entry 2025 (class 2604 OID 76295)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY security_categories ALTER COLUMN id SET DEFAULT nextval('security_categories_id_seq'::regclass);


--
-- TOC entry 2026 (class 2604 OID 76306)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY security_category_mappings ALTER COLUMN id SET DEFAULT nextval('security_category_mappings_id_seq'::regclass);


--
-- TOC entry 2028 (class 2604 OID 76325)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY security_server_client_names ALTER COLUMN id SET DEFAULT nextval('security_server_client_names_id_seq'::regclass);


--
-- TOC entry 2027 (class 2604 OID 76314)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY security_server_clients ALTER COLUMN id SET DEFAULT nextval('security_server_clients_id_seq'::regclass);


--
-- TOC entry 2029 (class 2604 OID 76333)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY security_servers ALTER COLUMN id SET DEFAULT nextval('security_servers_id_seq'::regclass);


--
-- TOC entry 2010 (class 2604 OID 58736)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY signed_xmls ALTER COLUMN id SET DEFAULT nextval('signed_xmls_id_seq'::regclass);


--
-- TOC entry 2030 (class 2604 OID 76350)
-- Name: id; Type: DEFAULT; Schema: public; Owner: centerui
--

ALTER TABLE ONLY system_parameters ALTER COLUMN id SET DEFAULT nextval('system_parameters_id_seq'::regclass);


--
-- TOC entry 2037 (class 2606 OID 76155)
-- Name: approved_tsps_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY approved_tsps
    ADD CONSTRAINT approved_tsps_pkey PRIMARY KEY (id);


--
-- TOC entry 2039 (class 2606 OID 76166)
-- Name: auth_certs_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY auth_certs
    ADD CONSTRAINT auth_certs_pkey PRIMARY KEY (id);


--
-- TOC entry 2041 (class 2606 OID 76177)
-- Name: ca_infos_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY ca_infos
    ADD CONSTRAINT ca_infos_pkey PRIMARY KEY (id);


--
-- TOC entry 2043 (class 2606 OID 76185)
-- Name: central_services_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY central_services
    ADD CONSTRAINT central_services_pkey PRIMARY KEY (id);


--
-- TOC entry 2077 (class 2606 OID 76366)
-- Name: distributed_files_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY distributed_files
    ADD CONSTRAINT distributed_files_pkey PRIMARY KEY (id);


--
-- TOC entry 2079 (class 2606 OID 76377)
-- Name: distributed_signed_files_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY distributed_signed_files
    ADD CONSTRAINT distributed_signed_files_pkey PRIMARY KEY (id);


--
-- TOC entry 2045 (class 2606 OID 76196)
-- Name: federated_sdsbs_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY federated_sdsbs
    ADD CONSTRAINT federated_sdsbs_pkey PRIMARY KEY (id);


--
-- TOC entry 2047 (class 2606 OID 76204)
-- Name: global_group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY global_group_members
    ADD CONSTRAINT global_group_members_pkey PRIMARY KEY (id);


--
-- TOC entry 2049 (class 2606 OID 76215)
-- Name: global_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY global_groups
    ADD CONSTRAINT global_groups_pkey PRIMARY KEY (id);


--
-- TOC entry 2051 (class 2606 OID 76226)
-- Name: identifiers_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY identifiers
    ADD CONSTRAINT identifiers_pkey PRIMARY KEY (id);


--
-- TOC entry 2053 (class 2606 OID 76234)
-- Name: member_class_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY member_class_mappings
    ADD CONSTRAINT member_class_mappings_pkey PRIMARY KEY (id);


--
-- TOC entry 2055 (class 2606 OID 76245)
-- Name: member_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY member_classes
    ADD CONSTRAINT member_classes_pkey PRIMARY KEY (id);


--
-- TOC entry 2057 (class 2606 OID 76256)
-- Name: ocsp_infos_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY ocsp_infos
    ADD CONSTRAINT ocsp_infos_pkey PRIMARY KEY (id);


--
-- TOC entry 2059 (class 2606 OID 76267)
-- Name: pkis_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY pkis
    ADD CONSTRAINT pkis_pkey PRIMARY KEY (id);


--
-- TOC entry 2063 (class 2606 OID 76289)
-- Name: request_processings_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY request_processings
    ADD CONSTRAINT request_processings_pkey PRIMARY KEY (id);


--
-- TOC entry 2061 (class 2606 OID 76278)
-- Name: requests_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY requests
    ADD CONSTRAINT requests_pkey PRIMARY KEY (id);


--
-- TOC entry 2065 (class 2606 OID 76300)
-- Name: security_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY security_categories
    ADD CONSTRAINT security_categories_pkey PRIMARY KEY (id);


--
-- TOC entry 2067 (class 2606 OID 76308)
-- Name: security_category_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY security_category_mappings
    ADD CONSTRAINT security_category_mappings_pkey PRIMARY KEY (id);


--
-- TOC entry 2071 (class 2606 OID 76327)
-- Name: security_server_client_names_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY security_server_client_names
    ADD CONSTRAINT security_server_client_names_pkey PRIMARY KEY (id);


--
-- TOC entry 2069 (class 2606 OID 76319)
-- Name: security_server_clients_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY security_server_clients
    ADD CONSTRAINT security_server_clients_pkey PRIMARY KEY (id);


--
-- TOC entry 2073 (class 2606 OID 76338)
-- Name: security_servers_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY security_servers
    ADD CONSTRAINT security_servers_pkey PRIMARY KEY (id);


--
-- TOC entry 2035 (class 2606 OID 58741)
-- Name: signed_xmls_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY signed_xmls
    ADD CONSTRAINT signed_xmls_pkey PRIMARY KEY (id);


--
-- TOC entry 2075 (class 2606 OID 76355)
-- Name: system_parameters_pkey; Type: CONSTRAINT; Schema: public; Owner: centerui; Tablespace: 
--

ALTER TABLE ONLY system_parameters
    ADD CONSTRAINT system_parameters_pkey PRIMARY KEY (id);


--
-- TOC entry 2033 (class 1259 OID 16614)
-- Name: unique_schema_migrations; Type: INDEX; Schema: public; Owner: centerui; Tablespace: 
--

CREATE UNIQUE INDEX unique_schema_migrations ON schema_migrations USING btree (version);


--
-- TOC entry 2192 (class 0 OID 0)
-- Dependencies: 5
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


-- Completed on 2013-11-28 15:09:30 EET

--
-- PostgreSQL database dump complete
--

