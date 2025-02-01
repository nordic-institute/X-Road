/*
 *  Copyright (c) 2024 Metaform Systems, Inc.
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
CREATE TABLE IF NOT EXISTS keypair_resource
(
  id                    VARCHAR PRIMARY KEY NOT NULL,               -- primary key
  participant_id        VARCHAR,                                    -- ID of the owning ParticipantContext. this is a loose business key, not a FK!
  timestamp             BIGINT              NOT NULL,               -- creation timestamp
  key_id                VARCHAR             NOT NULL,               -- name/key-id of this key pair. for use in JWTs etc.
  group_name            VARCHAR,
  is_default_pair       BOOLEAN                      DEFAULT FALSE, -- whether this keypair is the default one for a participant context
  use_duration          BIGINT,                                     -- maximum time this keypair can be active before it gets rotated
  rotation_duration     BIGINT,                                     -- duration during which this keypair is in a transitional state (rotated, not yet deactivated)
  serialized_public_key VARCHAR             NOT NULL,               -- serialized public key (PEM, JWK,...)
  private_key_alias     VARCHAR             NOT NULL,               -- alias under which the private key is stored in the HSM/Vault
  state                 INT                 NOT NULL DEFAULT 100,   -- KeyPairState
  key_context           VARCHAR                                     --the key context, will end up in the VerificationMethod of the DID Document
);
