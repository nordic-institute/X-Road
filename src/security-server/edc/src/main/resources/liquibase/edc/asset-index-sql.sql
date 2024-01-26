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
--       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
--

-- THIS SCHEMA HAS BEEN WRITTEN AND TESTED ONLY FOR POSTGRES

-- table: edc_asset
CREATE TABLE IF NOT EXISTS edc_asset
(
    asset_id           VARCHAR NOT NULL,
    created_at         BIGINT  NOT NULL,
    properties         JSON    DEFAULT '{}',
    private_properties JSON    DEFAULT '{}',
    data_address       JSON    DEFAULT '{}',
    PRIMARY KEY (asset_id)
);

COMMENT ON COLUMN edc_asset.properties IS 'Asset properties serialized as JSON';
COMMENT ON COLUMN edc_asset.private_properties IS 'Asset private properties serialized as JSON';
COMMENT ON COLUMN edc_asset.data_address IS 'Asset DataAddress serialized as JSON';
