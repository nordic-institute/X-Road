/*
 *  Copyright (c) 2023 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

-- only intended for and tested with Postgres!
CREATE TABLE IF NOT EXISTS did_resources
(
  did              VARCHAR NOT NULL,
  create_timestamp BIGINT  NOT NULL,
  state_timestamp  BIGINT  NOT NULL,
  state            INT     NOT NULL,
  did_document     JSON    NOT NULL,
  participant_id   VARCHAR,
  PRIMARY KEY (did)
  );
