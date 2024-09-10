--
--
--  This program and the accompanying materials are made available under the
--  terms of the Apache License, Version 2.0 which is available at
--  https://www.apache.org/licenses/LICENSE-2.0
--
--  SPDX-License-Identifier: Apache-2.0
--
--  Contributors:
--       Daimler TSS GmbH - Initial SQL Query
--       Microsoft Corporation - refactoring
--       SAP SE - add private properties to contract definition
--

-- table: edc_contract_definitions
-- only intended for and tested with H2 and Postgres!
CREATE TABLE IF NOT EXISTS edc_contract_definitions
(
  created_at             BIGINT  NOT NULL,
  contract_definition_id VARCHAR NOT NULL,
  access_policy_id       VARCHAR NOT NULL,
  contract_policy_id     VARCHAR NOT NULL,
  assets_selector        JSON    NOT NULL,
  private_properties     JSON,
  PRIMARY KEY (contract_definition_id)
  );
