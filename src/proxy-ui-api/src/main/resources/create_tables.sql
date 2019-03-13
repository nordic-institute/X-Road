CREATE TABLE apikey (
  id BIGINT PRIMARY KEY,
  encodedkey VARCHAR(255)
);

-- -- use same convention (bigserial id) as service_securitycategories
CREATE TABLE apikey_roles
(
    id BIGSERIAL PRIMARY KEY,
    apikey_id BIGINT NOT NULL,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT unique_apikey_role UNIQUE(apikey_id, role),
    CONSTRAINT valid_role CHECK (
            role IN ('XROAD_SECURITY_OFFICER', 'XROAD_REGISTRATION_OFFICER', 'XROAD_SERVICE_ADMINISTRATOR',
                     'XROAD_SYSTEM_ADMINISTRATOR', 'XROAD_SECURITYSERVER_OBSERVER')
        )
);

alter table apikey owner to serverconf;
alter table apikey_roles owner to serverconf;
