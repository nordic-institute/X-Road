/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.dataspace;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Reads the Issuer Service's key-pair table with a native query rather than a JPA entity: the Central Server
 * owns no part of that schema and this is its only query against it.
 * <p>
 * The query orders candidates by timestamp, newest first, so the result is the first Signing key by that
 * order if the XRDADR-48 invariant of exactly one Signing key is ever broken. A planned key switch has a
 * short window with no Signing key at all, in which case the result is empty.
 */
@Component
@Transactional
@RequiredArgsConstructor
class IssuerKeyPairReaderImpl {

    private static final int SIGNING_STATE = 200;

    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public Optional<String> findSigningKeyAlias() {
        List<String> aliases = entityManager.createNativeQuery(
                        "select private_key_alias from \"ds-issuer-service\".keypair_resource "
                                + "where participant_context_id = ?1 and state = ?2 and is_default_pair = true "
                                + "order by \"timestamp\" desc, private_key_alias",
                        String.class)
                .setParameter(1, DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID)
                .setParameter(2, SIGNING_STATE)
                .getResultList();
        return aliases.stream().findFirst();
    }
}
