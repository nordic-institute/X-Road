/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

-- Statements are designed for and tested with Postgres only!

CREATE TABLE IF NOT EXISTS edc_accesstokendata
(
  id           VARCHAR NOT NULL PRIMARY KEY,
  claim_token  JSON    NOT NULL,
  data_address JSON    NOT NULL,
  additional_properties JSON DEFAULT '{}'
);

COMMENT ON COLUMN edc_accesstokendata.claim_token IS 'ClaimToken serialized as JSON map';
COMMENT ON COLUMN edc_accesstokendata.data_address IS 'DataAddress serialized as JSON map';
COMMENT ON COLUMN edc_accesstokendata.additional_properties IS 'Optional Additional properties serialized as JSON map';
