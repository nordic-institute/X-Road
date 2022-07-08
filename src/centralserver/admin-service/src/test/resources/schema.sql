CREATE VIEW flattened_security_server_client AS
(SELECT c.id,
        c.server_client_id,
        c.member_code,
        c.name as member_name,
        c.type,
        c.member_class_id,
        c.subsystem_code,
        c.created_at,
        c.updated_at,
        i.xroad_instance
 FROM security_server_clients c
          LEFT JOIN identifiers i
                    ON c.server_client_id = i.id
 WHERE c.type = 'XRoadMember')
union
(SELECT c.id,
        c.server_client_id,
        m.member_code,
        m.name as member_name,
        c.type,
        m.member_class_id,
        c.subsystem_code,
        c.created_at,
        c.updated_at,
        i.xroad_instance
 FROM security_server_clients c
          LEFT JOIN security_server_clients m
                    ON c.xroad_member_id = m.id
          LEFT JOIN identifiers i
                    ON c.server_client_id = i.id
 WHERE c.type = 'Subsystem');

CREATE VIEW management_request_view
            (id,
             origin,
             comments,
             type,
             security_server_id,
             request_processing_id,
             request_processing_status,
             security_server_owner_name,
             xroad_instance,
             member_code,
             member_class,
             server_code,
             created_at)
AS
SELECT r.id,
       r.origin,
       r.comments,
       r.type,
       r.security_server_id,

       r.request_processing_id,
       rp.status AS request_processing_status,

       ssc.name  AS security_server_owner_name,
       i.xroad_instance,
       i.member_code,
       i.member_class,
       i.server_code,
       r.created_at
FROM requests r
         LEFT JOIN request_processings rp ON (rp.id = r.request_processing_id)
         LEFT JOIN identifiers i ON (i.id =r.security_server_id  )
         LEFT JOIN member_classes mc ON (mc.code = i.member_class)
         LEFT JOIN security_server_clients ssc
                   ON (ssc.member_code = i.member_code AND ssc.member_class_id = mc.id AND ssc.type = 'XRoadMember');
