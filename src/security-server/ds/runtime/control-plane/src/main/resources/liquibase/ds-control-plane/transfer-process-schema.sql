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

CREATE TABLE IF NOT EXISTS edc_transfer_process
(
  transferprocess_id       VARCHAR           NOT NULL
    CONSTRAINT transfer_process_pk
      PRIMARY KEY,
  type                       VARCHAR           NOT NULL,
  state                      INTEGER           NOT NULL,
  state_count                INTEGER DEFAULT 0 NOT NULL,
  state_time_stamp           BIGINT,
  created_at                 BIGINT            NOT NULL,
  updated_at                 BIGINT            NOT NULL,
  trace_context              JSON,
  error_detail               VARCHAR,
  resource_manifest          JSON,
  provisioned_resource_set   JSON,
  content_data_address       JSON,
  deprovisioned_resources    JSON,
  private_properties         JSON,
  callback_addresses         JSON,
  pending                    BOOLEAN  DEFAULT FALSE,
  transfer_type              VARCHAR,
  protocol_messages          JSON,
  data_plane_id              VARCHAR,
  correlation_id             VARCHAR,
  counter_party_address      VARCHAR,
  protocol                   VARCHAR,
  asset_id                   VARCHAR,
  contract_id                VARCHAR,
  data_destination           JSON,
  lease_id                   VARCHAR
    CONSTRAINT transfer_process_lease_lease_id_fk
      REFERENCES edc_lease
      ON DELETE SET NULL
);

COMMENT ON COLUMN edc_transfer_process.trace_context IS 'Java Map serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.resource_manifest IS 'java ResourceManifest serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.provisioned_resource_set IS 'ProvisionedResourceSet serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.content_data_address IS 'DataAddress serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.deprovisioned_resources IS 'List of deprovisioned resources, serialized as JSON';


CREATE UNIQUE INDEX IF NOT EXISTS transfer_process_id_uindex
  ON edc_transfer_process (transferprocess_id);

CREATE UNIQUE INDEX IF NOT EXISTS lease_lease_id_uindex
  ON edc_lease (lease_id);

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS transfer_process_state ON edc_transfer_process (state,state_time_stamp);
