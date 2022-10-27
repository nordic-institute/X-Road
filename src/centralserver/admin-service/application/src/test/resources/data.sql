-- noinspection SqlNoDataSourceInspectionForFile

-- noinspection SqlInsertValuesForFile

-- data populated for the integration tests
-- SQL needs to be defined in terms of autogenerated HSQL table structure, not the actual PostgreSQL tables.
INSERT INTO ui_users
       (id,      username,   locale,   created_at,   updated_at)
VALUES (1000001, 'testuser', null,     now(),        now());
ALTER SEQUENCE ui_users_id_seq RESTART WITH 1001000;

-- noinspection SqlResolve
-- INSERT INTO apikey
--        (id,   encodedkey)
-- VALUES (1,    'ad26a8235b3e847dc0b9ac34733d5acb39e2b6af634796e7eebe171165cdf2d1');
-- ALTER SEQUENCE apikey_id_seq RESTART WITH 1001000;
--
-- INSERT INTO apikey_roles
--        (id,    apikey_id, role)
-- VALUES (1000001, 1,       'XROAD_SYSTEM_ADMINISTRATOR'),
--        (1000002, 1,       'XROAD_SECURITY_OFFICER'),
--        (1000003, 1,       'XROAD_REGISTRATION_OFFICER');
-- ALTER SEQUENCE apikey_roles_id_seq RESTART WITH 1001000;


INSERT INTO member_classes
       (id,      code,             description,                          created_at,                   updated_at)
VALUES (1000001, 'GOV',            'Government',                         now(),                        now()),
       (1000002, 'MemberclassFoo', 'Member class with rare code string', now(),                        now()),
       (1000701, 'ORG',            'Organizations',                      '2022-03-07 07:33:22.654029', '2022-03-07 07:33:22.654029');
ALTER SEQUENCE member_classes_id_seq RESTART WITH 1001000;

INSERT INTO identifiers
       (id,      object_type, xroad_instance, member_class,     member_code, subsystem_code, created_at, updated_at)
VALUES (1000001, 'MEMBER',    'TEST',         'GOV',            'M1',        null,           now(),      now()),
       (1000002, 'MEMBER',    'TEST',         'GOV',            'M2',        null,           now(),      now()),
       (1000003, 'MEMBER',    'TEST',         'GOV',            'M3',        null,           now(),      now()),
       (1000004, 'MEMBER',    'TEST',         'GOV',            'M4',        null,           now(),      now()),
       (1000005, 'MEMBER',    'TEST',         'GOV',            'M5',        null,           now(),      now()),
       (1000006, 'MEMBER',    'TEST',         'GOV',            'M6',        null,           now(),      now()),
       (1000007, 'MEMBER',    'TEST',         'GOV',            'M7',        null,           now(),      now()),
       (1000008, 'MEMBER',    'TEST',         'GOV',            'M8',        null,           now(),      now()),
       (1000009, 'MEMBER',    'TEST',         'GOV',            'M9',        null,           now(),      now()),
       (1000010, 'SUBSYSTEM', 'TEST',         'GOV',            'M1',        'SS1',          now(),      now()),
       (1000011, 'SUBSYSTEM', 'TEST',         'MemberclassFoo', 'M10',       null,           now(),      now()),
       (1000012, 'MEMBER',    'Instance2',    'GOV',            'M11',       null,           now(),      now());
INSERT INTO identifiers
       (id,      object_type, xroad_instance, member_class, member_code, subsystem_code, service_code, server_code, service_version, created_at, updated_at)
VALUES (1000701, 'MEMBER',    'TEST',         'ORG',        '111',       null,           null,         null,        null,              now(),        now()),
       (1000702, 'SUBSYSTEM', 'TEST',         'ORG',        '111',       'MANAGEMENT',   null,         null,        null,              now(),        now()),
       (1000703, 'SERVER',    'TEST',         'ORG',        '111',       null,           null,         'ADMINSS',   null,              now(),        now()),
       (1000704, 'SUBSYSTEM', 'TEST',         'ORG',        '222',       'TEST',         null,         'SERVICESS', null,              now(),        now()),
       (1000705, 'MEMBER',    'TEST',         'GOV',        '333',       null,           null,         null,        null,              now(),        now()),
       (1000706, 'MEMBER',    'TEST',         'ORG',        '000',       null,           null,         null,        null,              now(),        now()),
       (1000707, 'MEMBER',    'TEST',         'GOV',        '321',       null,           null,         null,        null,              now(),        now());
ALTER SEQUENCE identifiers_id_seq RESTART WITH 1001000;

INSERT INTO security_server_clients
       (id,      member_code, subsystem_code, name,             xroad_member_id, member_class_id,   server_client_id, type,            created_at, updated_at)
VALUES (1000001, 'M1',        null,           'Member1',        null,            1000001,           1000001,          'XRoadMember',   now(),        now()),
       (1000002, 'M2',        null,           'Member2',        null,            1000001,           1000002,          'XRoadMember',   now(),        now()),
       (1000003, 'M3',        null,           'member3',        null,            1000001,           1000003,          'XRoadMember',   now(),        now()),
       (1000004, 'M4',        null,           'mEmber4',        null,            1000001,           1000004,          'XRoadMember',   now(),        now()),
       (1000005, 'M5',        null,           'Member5-ÅÖÄ',    null,            1000001,           1000005,          'XRoadMember',   now(),        now()),
       (1000006, 'M6',        null,           'Member6\a',      null,            1000001,           1000006,          'XRoadMember',   now(),        now()),
       (1000007, 'M7',        null,           'Member7_a',      null,            1000001,           1000007,          'XRoadMember',   now(),        now()),
       (1000008, 'M8',        null,           'Member8%a',      null,            1000001,           1000008,          'XRoadMember',   now(),        now()),
       (1000009, 'M9',        null,           'Member9__%%em%', null,            1000001,           1000009,          'XRoadMember',   now(),        now()),
       (1000010, null,        'SS1',          'Member1-SS1',    1000001,         1000001,           1000010,          'Subsystem',     now(),        now()),
       (1000011, 'M10',       null,           'Member10',       null,            1000002,           1000011,          'XRoadMember',   now(),        now()),
       (1000012, 'M11',       null,           'Member11',       null,            1000001,           1000012,          'XRoadMember',   now(),        now());
INSERT INTO security_server_clients
       (id,      member_code, subsystem_code, name,     xroad_member_id, member_class_id, server_client_id, type,          administrative_contact, created_at,                   updated_at)
VALUES (1000701, '111',       null,           'ADMORG', null,            1000701,         1000701,          'XRoadMember', null,                   '2022-03-07 07:44:57.466408', '2022-03-07 07:44:57.466408'),
       (1000702, null,        'MANAGEMENT',   null,     1000701,         null,            1000702,          'Subsystem',   null,                   '2022-03-07 07:45:34.809253', '2022-03-07 07:45:34.809253'),
       (1000703, null,        'TEST',         null,     1000701,         null,            1000704,          'Subsystem',   null,                   '2022-03-07 08:48:15.393806', '2022-03-07 08:48:15.393806'),
       (1000704, '222',       null,           'TEST2',  null,            1000001,         1000705,          'XRoadMember', null,                   '2022-03-07 08:48:15.393806', '2022-03-07 08:48:15.393806'),
       (1000705, '221',       null,           'TEST3',  null,            1000701,         1000706,          'XRoadMember', null,                   '2022-03-18 12:18:15.393806', '2022-03-18 12:48:15.393806'),
       (1000706, '223',       null,           'TEST4',  null,            1000001,         1000707,          'XRoadMember', null,                   '2022-03-21 12:18:15.333333', '2022-03-21 12:48:15.333333');
ALTER SEQUENCE security_server_clients_id_seq RESTART WITH 1001000;

INSERT INTO security_servers
       (id,      server_code,       owner_id, address,         created_at,                   updated_at)
VALUES (1000701, 'ADMINSS2',        1000701,  '10.85.227.109', '2022-03-07 08:27:17.395387', '2022-03-07 08:27:17.395387'),
       (1000702, 'SERVICESS2_CODE', 1000705,  '10.85.227.239', '2022-03-15 08:27:17.222222', '2022-03-15 08:27:17.222222'),
       (1000703, 'SERVICESS1_CODE', 1000704,  '10.85.227.111', '2022-03-15 08:27:17.111111', '2022-03-15 08:27:17.111111'),
       (1000704, 'SERVICESS3_CODE', 1000706,  '10.85.227.333', '2022-03-21 08:25:17.333333', '2022-03-21 08:25:17.333333'),
       (1000001, 'server1',         1000001,  'server1.test',  now(),                        now()),
       (1000002, 'server2',         1000004,  'server2.test',  now(),                        now());
ALTER SEQUENCE security_servers_id_seq RESTART WITH 1001000;

INSERT INTO server_clients
       (id,      security_server_id, security_server_client_id)
VALUES (1000001,   1000001,              1000001),
       (1000002,   1000001,              1000002),
       (1000003,   1000001,              1000010),
       (1000004,   1000002,              1000004),
       (1000005,   1000002,              1000010),
       (1000701,   1000701,              1000702),
       (1000702,   1000701,              1000703),
       (1000703,   1000702,              1000703),
       (1000704,   1000703,              1000704);
ALTER SEQUENCE server_clients_id_seq RESTART WITH 1001000;

INSERT INTO ca_infos
       (id,  valid_from, valid_to, created_at, updated_at)
VALUES (100, now(),      now(),    now(),      now());

INSERT INTO approved_cas
       (id,  top_ca_id, name,                created_at, updated_at)
VALUES (100, 100,       'X-Road Test CA CN', now(),     now());

INSERT INTO global_groups
       (id,      group_code, description,           member_count, created_at, updated_at)
VALUES (1000001, 'CODE_1',   'First global group',  1,            now(),      now()),
       (1000002, 'CODE_2',   'Second global group', 2,            now(),      now()),
       (1000003, 'CODE_3',   'Third global group',  0,            now(),      now());

INSERT INTO global_group_members
       (id,      global_group_id, group_member_id, created_at, updated_at)
VALUES (1000001, 1000001,         1000010,         now(),      now()),
       (1000002, 1000002,         1000011,         now(),      now()),
       (1000003, 1000002,         1000010,         now(),      now());

INSERT INTO request_processings (id, type, status, created_at, updated_at) VALUES (1000, 'AuthCertRegProcessing', 'APPROVED', '2021-03-10 08:24:59.913689', '2021-03-10 08:25:00.240286');
INSERT INTO requests (id, request_processing_id, type, security_server_id, sec_serv_user_id, auth_cert, address, origin,
                        comments, created_at, updated_at) VALUES (2000, 1000, 'AuthCertRegRequest', 1000703, null, null, 'ss1', 'SECURITY_SERVER', null, '2021-03-10 08:24:59.930557', '2021-03-10 08:25:00.250267');
INSERT INTO requests (id, request_processing_id, type, security_server_id, sec_serv_user_id, auth_cert, address, origin,
                        comments, created_at, updated_at) VALUES (2001, 1000, 'AuthCertRegRequest', 1000703, null, null, 'ss1', 'CENTER', null, '2021-03-10 08:24:59.984921', '2021-03-10 08:25:00.246212');
