
CREATE TABLE IF NOT EXISTS edc_edr_entry
(
   transfer_process_id           VARCHAR NOT NULL PRIMARY KEY,
   agreement_id                  VARCHAR NOT NULL,
   asset_id                      VARCHAR NOT NULL,
   provider_id                   VARCHAR NOT NULL,
   contract_negotiation_id       VARCHAR,
   created_at                    BIGINT  NOT NULL
);

