-- THIS SCHEMA HAS BEEN WRITTEN AND TESTED ONLY FOR POSTGRES

-- table: edc_sts_client

CREATE TABLE IF NOT EXISTS edc_sts_client
(
  id                                   VARCHAR NOT NULL PRIMARY KEY,
  client_id                            VARCHAR NOT NULL,
  did                                  VARCHAR NOT NULL,
  name                                 VARCHAR NOT NULL,
  secret_alias                         VARCHAR NOT NULL,
  private_key_alias                    VARCHAR NOT NULL,
  public_key_reference                 VARCHAR NOT NULL,
  created_at                           BIGINT  NOT NULL
);


CREATE UNIQUE INDEX IF NOT EXISTS sts_client_client_id_index ON edc_sts_client (client_id);
