/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

/**
 * Writes binding rows straight to the database for tests that run outside a test transaction. Every write commits in
 * its own transaction.
 */
@RequiredArgsConstructor
public class ParticipantRowTestUtils {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    /**
     * Inserts an active SYSTEM binding row.
     *
     * @param id               the row id
     * @param did              the bound SYSTEM DID
     * @param publicationState the publication state of the row
     */
    public void systemRow(long id, String did, PublicationState publicationState) {
        inNewTransaction(() -> jdbcTemplate.update("INSERT INTO ds_participant"
                        + " (id, participant_type, ctx_id, did, scheme_version, state, publication_state, created_at,"
                        + " updated_at) VALUES (?, 'SYSTEM', ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP,"
                        + " CURRENT_TIMESTAMP)",
                id, ParticipantIdentifierScheme.SYSTEM_SEGMENT, did, ParticipantIdentifierScheme.SCHEME_VERSION,
                publicationState.name()));
    }

    /**
     * Inserts an active MEMBER binding row.
     *
     * @param id               the row id
     * @param member           the member the row is bound to
     * @param did              the bound member DID
     * @param publicationState the publication state of the row
     */
    public void memberRow(long id, ClientId member, String did, PublicationState publicationState) {
        memberRow(id, member, did, ParticipantState.ACTIVE, publicationState);
    }

    /**
     * Inserts a MEMBER binding row.
     *
     * @param id               the row id
     * @param member           the member the row is bound to
     * @param did              the bound member DID
     * @param state            the lifecycle state of the row
     * @param publicationState the publication state of the row
     */
    public void memberRow(long id, ClientId member, String did, ParticipantState state,
            PublicationState publicationState) {
        inNewTransaction(() -> jdbcTemplate.update("INSERT INTO ds_participant"
                        + " (id, participant_type, member_identifier, ctx_id, did, scheme_version, state,"
                        + " publication_state, created_at, updated_at)"
                        + " VALUES (?, 'MEMBER', (SELECT id FROM identifier WHERE object_type = 'MEMBER'"
                        + " AND xroad_instance = ? AND member_class = ? AND member_code = ?),"
                        + " ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, member.getXRoadInstance(), member.getMemberClass(), member.getMemberCode(),
                ParticipantIdentifierScheme.memberCtxId(member), did, ParticipantIdentifierScheme.SCHEME_VERSION,
                state.name(), publicationState.name()));
    }

    /**
     * Deletes binding rows.
     *
     * @param ids the ids of the rows to delete
     */
    public void deleteRows(long... ids) {
        inNewTransaction(() -> Arrays.stream(ids).forEach(id ->
                jdbcTemplate.update("DELETE FROM ds_participant WHERE id = ?", id)));
    }

    /**
     * Runs the work in a new transaction that commits on return.
     *
     * @param work the work to run
     */
    public void inNewTransaction(Runnable work) {
        new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(PROPAGATION_REQUIRES_NEW))
                .executeWithoutResult(_ -> work.run());
    }
}
