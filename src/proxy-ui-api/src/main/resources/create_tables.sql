-- CREATE TABLE role (
--     id BIGINT PRIMARY KEY,
--     code VARCHAR(255) UNIQUE
-- );

CREATE TABLE apikey (
  id BIGINT PRIMARY KEY,
  encodedkey VARCHAR(255)
);

CREATE TABLE apikey_roles
(
    id BIGINT PRIMARY KEY,
    apikey_id BIGINT NOT NULL,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT unique_apikey_role UNIQUE(apikey_id, role),
    CONSTRAINT valid_role CHECK (
            role IN ("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER", "XROAD_SERVICE_ADMINISTRATOR",
                     "XROAD_SYSTEM_ADMINISTRATOR", "XROAD_SECURITYSERVER_OBSERVER")
        )
);


-- -- use same convention (bigserial id) as service_securitycategories
-- CREATE TABLE apikey_roles (
--                               id BIGSERIAL UNIQUE,
--                               apikey_id BIGINT NOT NULL REFERENCES apikey(id),
--                               role_id bigint not null references role(id),
--                               CONSTRAINT unique_key UNIQUE(apikey_id, role_id)
-- );

INSERT INTO role (id, code) VALUES (1, 'XROAD_SECURITY_OFFICER');
INSERT INTO role (id, code) VALUES (2, 'XROAD_REGISTRATION_OFFICER');
INSERT INTO role (id, code) VALUES (3, 'XROAD_SERVICE_ADMINISTRATOR');
INSERT INTO role (id, code) VALUES (4, 'XROAD_SYSTEM_ADMINISTRATOR');
INSERT INTO role (id, code) VALUES (5, 'XROAD_SECURITYSERVER_OBSERVER');

