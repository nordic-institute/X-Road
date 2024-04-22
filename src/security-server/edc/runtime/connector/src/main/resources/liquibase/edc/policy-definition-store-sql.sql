--
--
--  This program and the accompanying materials are made available under the
--  terms of the Apache License, Version 2.0 which is available at
--  https://www.apache.org/licenses/LICENSE-2.0
--
--  SPDX-License-Identifier: Apache-2.0
--
--  Contributors:
--       ZF Friedrichshafen AG - Initial SQL Query
--

-- Statements are designed for and tested with Postgres only!

-- table: edc_policydefinitions
CREATE TABLE IF NOT EXISTS edc_policydefinitions
(
  policy_id             VARCHAR NOT NULL,
  created_at            BIGINT  NOT NULL,
  permissions           JSON,
  prohibitions          JSON,
  duties                JSON,
  extensible_properties JSON,
  inherits_from         VARCHAR,
  assigner              VARCHAR,
  assignee              VARCHAR,
  target                VARCHAR,
  policy_type           VARCHAR NOT NULL,
  private_properties    JSON,
  PRIMARY KEY (policy_id)
  );

COMMENT ON COLUMN edc_policydefinitions.permissions IS 'Java List<Permission> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.prohibitions IS 'Java List<Prohibition> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.duties IS 'Java List<Duty> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.extensible_properties IS 'Java Map<String, Object> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.policy_type IS 'Java PolicyType serialized as JSON';

CREATE UNIQUE INDEX IF NOT EXISTS edc_policydefinitions_id_uindex
  ON edc_policydefinitions (policy_id);
