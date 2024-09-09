CREATE TABLE IF NOT EXISTS edc_lease
(
  leased_by      VARCHAR NOT NULL,
  leased_at      BIGINT,
  lease_duration INTEGER NOT NULL,
  lease_id       VARCHAR NOT NULL
  CONSTRAINT lease_pk
  PRIMARY KEY
);


CREATE TABLE IF NOT EXISTS edc_data_plane_instance
(
  id                   VARCHAR NOT NULL PRIMARY KEY,
  data                 JSON,
  lease_id             VARCHAR
  CONSTRAINT data_plane_instance_lease_id_fk
  REFERENCES edc_lease
  ON DELETE SET NULL
);
