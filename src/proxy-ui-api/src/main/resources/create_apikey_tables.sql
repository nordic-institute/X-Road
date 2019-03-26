CREATE TABLE apikey (
  id BIGINT PRIMARY KEY,
  encodedkey VARCHAR(255) NOT NULL
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

ALTER TABLE apikey OWNER TO serverconf;
ALTER TABLE apikey_roles OWNER TO serverconf;

DROP TRIGGER IF EXISTS update_history ON apikey;
CREATE TRIGGER update_history AFTER INSERT OR UPDATE OR DELETE ON apikey_roles
    FOR EACH ROW EXECUTE PROCEDURE add_history_rows();

DROP TRIGGER IF EXISTS update_history ON apikey;
CREATE TRIGGER update_history AFTER INSERT OR UPDATE OR DELETE ON apikey
    FOR EACH ROW EXECUTE PROCEDURE add_history_rows();

