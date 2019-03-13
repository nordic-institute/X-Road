create table role (
    id bigint primary key,
    code varchar(255) unique
);

create table apikey (
  id bigint primary key,
  encodedkey varchar(255)
);

create table apikey_roles (
  apikey_id bigint not null references apikey(id),
  role_id bigint not null references role(id),
  constraint unique_key unique(apikey_id, role_id)
);

insert into role (id, code) values (1, 'XROAD_SECURITY_OFFICER');
insert into role (id, code) values (2, 'XROAD_REGISTRATION_OFFICER');
insert into role (id, code) values (3, 'XROAD_SERVICE_ADMINISTRATOR');
insert into role (id, code) values (4, 'XROAD_SYSTEM_ADMINISTRATOR');
insert into role (id, code) values (5, 'XROAD_SECURITYSERVER_OBSERVER');

