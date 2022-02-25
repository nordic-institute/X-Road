CREATE VIEW flattened_security_server_client AS
(SELECT c.id,
        c.server_client_id,
        c.member_code,
        c.name as member_name,
        c.type,
        c.member_class_id,
        c.subsystem_code
 FROM security_server_clients c
 WHERE c.type = 'XRoadMember')
union
(SELECT c.id,
        c.server_client_id,
        m.member_code,
        m.name as member_name,
        c.type,
        m.member_class_id,
        c.subsystem_code
 FROM security_server_clients c
          LEFT JOIN security_server_clients m
                    ON c.xroad_member_id = m.id
 WHERE c.type = 'Subsystem');
