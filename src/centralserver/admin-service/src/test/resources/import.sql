-- noinspection SqlInsertValuesForFile

-- data populated for the integration tests
-- SQL needs to be defined in terms of autogenerated HSQL table structure, not the actual PostgreSQL tables.
INSERT INTO UI_USERS(ID, USERNAME, LOCALE, CREATED_AT, UPDATED_AT) values (1, 'testuser', null, now(), now());
-- noinspection SqlResolve
INSERT INTO APIKEY (ID, ENCODED_KEY) values (1, 'ad26a8235b3e847dc0b9ac34733d5acb39e2b6af634796e7eebe171165cdf2d1');
INSERT INTO APIKEY_ROLES (APIKEY_ID, ROLE) values (1, 'XROAD_SYSTEM_ADMINISTRATOR');
INSERT INTO APIKEY_ROLES (APIKEY_ID, ROLE) values (1, 'XROAD_SECURITY_OFFICER');
INSERT INTO APIKEY_ROLES (APIKEY_ID, ROLE) values (1, 'XROAD_REGISTRATION_OFFICER');
-- identifiers
-- create table identifiers (type varchar(31) not null, id bigint not null, object_type varchar(255), xroad_instance varchar(255), member_class varchar(255), member_code varchar(255), subsystem_code varchar(255), server_code varchar(255), service_code varchar(255), service_version varchar(255), primary key (id));
INSERT INTO identifiers(id, object_type, xroad_instance, member_class, member_code, subsystem_code, service_code, server_code, type, service_version) VALUES (701, 'MEMBER', 'DEV', 'ORG', '111', null, null, null, 'ClientId', null);
INSERT INTO identifiers(id, object_type, xroad_instance, member_class, member_code, subsystem_code, service_code, server_code, type, service_version) VALUES (702, 'SUBSYSTEM', 'DEV', 'ORG', '111', 'MANAGEMENT', null, null, 'ClientId', null);
INSERT INTO identifiers(id, object_type, xroad_instance, member_class, member_code, subsystem_code, service_code, server_code, type, service_version) VALUES (703, 'SERVER', 'DEV', 'ORG', '111', null, null, 'ADMINSS', 'SecurityServerId', null);

INSERT INTO identifiers(id, object_type, xroad_instance, member_class, member_code, subsystem_code, service_code, server_code, type, service_version) VALUES (704, 'SERVER', 'DEV', 'ORG', '222', null, null, 'SERVICESS', 'SecurityServerId', null);
INSERT INTO identifiers(id, object_type, xroad_instance, member_class, member_code, subsystem_code, service_code, server_code, type, service_version) VALUES (705, 'MEMBER', 'DEV', 'GOV', '333', null, null, null, 'ClientId', null);



INSERT INTO MEMBER_CLASSES(id, code, description, created_at, updated_at) VALUES (701, 'ORG', 'Organizations', '2022-03-07 07:33:22.654029', '2022-03-07 07:33:22.654029');
INSERT INTO MEMBER_CLASSES(id, code, description, created_at, updated_at) VALUES (702, 'GOV', 'Governments', '2022-03-17 12:39:22.222222', '2022-03-17 12:39:22.222222');
INSERT INTO SECURITY_SERVER_CLIENTS(id, member_code, subsystem_code, name, xroad_member_id, member_class_id, server_client_id, type, administrative_contact, created_at, updated_at) VALUES (701, '111', null, 'ADMORG', null, 701, 701, 'XRoadMember', null, '2022-03-07 07:44:57.466408', '2022-03-07 07:44:57.466408');
INSERT INTO security_server_clients(id, member_code, subsystem_code, name, xroad_member_id, member_class_id, server_client_id, type, administrative_contact, created_at, updated_at) VALUES (702, null, 'MANAGEMENT', null, 701, null, 702, 'Subsystem', null, '2022-03-07 07:45:34.809253', '2022-03-07 07:45:34.809253');
INSERT INTO security_server_clients(id, member_code, subsystem_code, name, xroad_member_id, member_class_id, server_client_id, type, administrative_contact, created_at, updated_at) VALUES (703, null, 'TEST', null, 701, null, 704, 'Subsystem', null, '2022-03-07 08:48:15.393806', '2022-03-07 08:48:15.393806');
INSERT INTO security_server_clients(id, member_code, subsystem_code, name, xroad_member_id, member_class_id, server_client_id, type, administrative_contact, created_at, updated_at) VALUES (704, '222', null, 'TEST2', null, 702, 705, 'XRoadMember', null, '2022-03-07 08:48:15.393806', '2022-03-07 08:48:15.393806');

INSERT INTO security_servers(id, server_code, owner_id, address, created_at, updated_at) VALUES (701, 'ADMINSS', 701, '10.85.227.109', '2022-03-07 08:27:17.395387', '2022-03-07 08:27:17.395387');
INSERT INTO security_servers(id, server_code, owner_id, address, created_at, updated_at) VALUES (702, 'SERVICESS1_CODE', 701, '10.85.227.239', '2022-03-15 08:27:17.999999', '2022-03-15 08:27:17.999999');
INSERT INTO security_servers(id, server_code, owner_id, address, created_at, updated_at) VALUES (703, 'SERVICESS2_CODE', 704, '10.85.227.111', '2022-03-15 08:27:17.999999', '2022-03-15 08:27:17.999999');

INSERT INTO security_clients(id, security_server_id, security_server_client_id) VALUES (701, 701, 702);
INSERT INTO security_clients(id, security_server_id, security_server_client_id) VALUES (702, 701, 703);
INSERT INTO security_clients(id, security_server_id, security_server_client_id) VALUES (703, 702, 703);
INSERT INTO security_clients(id, security_server_id, security_server_client_id) VALUES (704, 703, 704);
