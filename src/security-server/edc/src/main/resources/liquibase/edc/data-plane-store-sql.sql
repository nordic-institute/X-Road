-- Statements are designed for and tested with Postgres only!

CREATE TABLE IF NOT EXISTS edc_lease
(
  leased_by      VARCHAR NOT NULL,
  leased_at      BIGINT,
  lease_duration INTEGER NOT NULL,
  lease_id       VARCHAR NOT NULL
  CONSTRAINT lease_pk
  PRIMARY KEY
);

COMMENT ON COLUMN edc_lease.leased_at IS 'posix timestamp of lease';
COMMENT ON COLUMN edc_lease.lease_duration IS 'duration of lease in milliseconds';

CREATE TABLE IF NOT EXISTS edc_data_plane
(
  process_id           VARCHAR NOT NULL PRIMARY KEY,
  state                INTEGER NOT NULL            ,
  created_at           BIGINT  NOT NULL            ,
  updated_at           BIGINT  NOT NULL            ,
  state_count          INTEGER DEFAULT 0 NOT NULL,
  state_time_stamp     BIGINT,
  trace_context        JSON,
  error_detail         VARCHAR,
  callback_address     VARCHAR,
  trackable            BOOLEAN,
  lease_id             VARCHAR
  CONSTRAINT data_plane_lease_lease_id_fk
  REFERENCES edc_lease
  ON DELETE SET NULL,
  source               JSON,
  destination          JSON,
  properties           JSON
);

COMMENT ON COLUMN edc_data_plane.trace_context IS 'Java Map serialized as JSON';
COMMENT ON COLUMN edc_data_plane.source IS 'DataAddress serialized as JSON';
COMMENT ON COLUMN edc_data_plane.destination IS 'DataAddress serialized as JSON';
COMMENT ON COLUMN edc_data_plane.properties IS 'Java Map serialized as JSON';
