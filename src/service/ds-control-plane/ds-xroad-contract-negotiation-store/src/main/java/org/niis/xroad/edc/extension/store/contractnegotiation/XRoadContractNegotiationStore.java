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

package org.niis.xroad.edc.extension.store.contractnegotiation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.spi.SqlLeaseContextBuilder;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.edc.extension.store.contractnegotiation.schema.XRoadContractNegotiationStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Sole {@link ContractNegotiationStore} provider for the control-plane runtime, taking over from the excluded
 * stock SQL store extension. Delegates every read/lease/query method to the wrapped stock store, and forks only
 * the save path to converge same-context self-negotiation onto one shared agreement row.
 *
 * <p>Forked save, single transaction: look up the agreement row for the (agreement id, participant context id)
 * composite pair; if it does not exist yet, or its content differs from the incoming agreement (excluding
 * {@code claims}), upsert the agreement with the composite pair as the conflict arbiter instead of the internal
 * primary key, then re-read the composite pair to learn the surviving internal id (race-free within the
 * transaction, since Postgres resolves the conflict atomically and never touches the primary-key column). The
 * negotiation row is then upserted with that surviving id as its agreement foreign key, and the lease is broken
 * as stock does.
 *
 * <p>The re-read/skip step is the race guard: stock re-upserts the whole agreement row on every negotiation save
 * that carries one, which would otherwise let either side of a self-negotiation clobber the other's most recent
 * write on every subsequent state-only save. Skipping the upsert when content is unchanged closes that window.
 *
 * <p>{@code claims} is excluded from the change comparison on purpose: it is the only column that genuinely
 * differs between the two sides of a self-negotiation (each records the counterparty's claims, written at
 * different moments), so it is last-writer-wins whenever a genuine content change does trigger the upsert. Claims
 * are informational/audit-only and not read by any authorization or policy-evaluation path; no merge machinery.
 */
public class XRoadContractNegotiationStore extends AbstractSqlStore implements ContractNegotiationStore {

    private final ContractNegotiationStore delegate;
    private final XRoadContractNegotiationStatements statements;
    private final SqlLeaseContextBuilder leaseContext;

    public XRoadContractNegotiationStore(ContractNegotiationStore delegate,
                                          DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                          TransactionContext transactionContext, ObjectMapper objectMapper,
                                          XRoadContractNegotiationStatements statements,
                                          SqlLeaseContextBuilder leaseContext, QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.delegate = delegate;
        this.statements = statements;
        this.leaseContext = leaseContext;
    }

    @Override
    public ContractNegotiation findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<ContractNegotiation> nextNotLeased(int max, Criterion... criteria) {
        return delegate.nextNotLeased(max, criteria);
    }

    @Override
    public StoreResult<ContractNegotiation> findByIdAndLease(String id) {
        return delegate.findByIdAndLease(id);
    }

    @Override
    public StoreResult<Void> save(ContractNegotiation negotiation) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var contractAgreement = negotiation.getContractAgreement();
                String agreementForeignKey = null;

                if (contractAgreement != null) {
                    agreementForeignKey = convergeAgreement(connection, contractAgreement);
                }

                upsertNegotiation(connection, negotiation, agreementForeignKey);

                return leaseContext.withConnection(connection).breakLease(negotiation.getId());
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public StoreResult<Void> breakLease(ContractNegotiation negotiation) {
        return delegate.breakLease(negotiation);
    }

    @Override
    public ContractAgreement findContractAgreement(String contractId) {
        return delegate.findContractAgreement(contractId);
    }

    @Override
    public StoreResult<Void> deleteById(String negotiationId) {
        return delegate.deleteById(negotiationId);
    }

    @Override
    public Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {
        return delegate.queryNegotiations(querySpec);
    }

    @Override
    public Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
        return delegate.queryAgreements(querySpec);
    }

    /**
     * Resolves the internal id of the agreement row that the composite pair (agreement id, participant context id)
     * declares authoritative, rewriting the row first if this save cycle actually changed its content.
     */
    private String convergeAgreement(Connection connection, ContractAgreement contractAgreement) throws SQLException {
        var findStatement = statements.getFindAgreementByCompositeKeyTemplate();
        var existing = queryExecutor.single(connection, false, this::mapAgreementSnapshot, findStatement,
                contractAgreement.getAgreementId(), contractAgreement.getParticipantContextId());

        if (existing == null || hasChanged(existing, contractAgreement)) {
            upsertAgreement(connection, contractAgreement);
            existing = queryExecutor.single(connection, false, this::mapAgreementSnapshot, findStatement,
                    contractAgreement.getAgreementId(), contractAgreement.getParticipantContextId());
        }

        return existing.id();
    }

    private boolean hasChanged(AgreementSnapshot existing, ContractAgreement incoming) {
        return !Objects.equals(existing.providerId(), incoming.getProviderId())
                || !Objects.equals(existing.consumerId(), incoming.getConsumerId())
                || existing.contractSigningDate() != incoming.getContractSigningDate()
                || !Objects.equals(existing.assetId(), incoming.getAssetId())
                || !Objects.equals(existing.policyJson(), toJson(incoming.getPolicy()));
    }

    private void upsertAgreement(Connection connection, ContractAgreement contractAgreement) {
        queryExecutor.execute(connection, statements.getUpsertAgreementTemplate(),
                contractAgreement.getId(),
                contractAgreement.getProviderId(),
                contractAgreement.getConsumerId(),
                contractAgreement.getContractSigningDate(),
                contractAgreement.getAssetId(),
                toJson(contractAgreement.getPolicy()),
                contractAgreement.getParticipantContextId(),
                contractAgreement.getAgreementId(),
                toJson(contractAgreement.getClaims()));
    }

    private void upsertNegotiation(Connection connection, ContractNegotiation negotiation, String agreementForeignKey) {
        queryExecutor.execute(connection, statements.getUpsertNegotiationTemplate(),
                negotiation.getId(),
                negotiation.getCorrelationId(),
                negotiation.getCounterPartyId(),
                negotiation.getCounterPartyAddress(),
                negotiation.getType().name(),
                negotiation.getProtocol(),
                negotiation.getState(),
                negotiation.getStateCount(),
                negotiation.getStateTimestamp(),
                negotiation.getErrorDetail(),
                agreementForeignKey,
                toJson(negotiation.getContractOffers()),
                toJson(negotiation.getCallbackAddresses()),
                toJson(negotiation.getTraceContext()),
                negotiation.getCreatedAt(),
                negotiation.getUpdatedAt(),
                negotiation.isPending(),
                toJson(negotiation.getProtocolMessages()),
                negotiation.getParticipantContextId());
    }

    private AgreementSnapshot mapAgreementSnapshot(ResultSet resultSet) throws SQLException {
        return new AgreementSnapshot(
                resultSet.getString(statements.getContractAgreementIdColumn()),
                resultSet.getString(statements.getProviderAgentColumn()),
                resultSet.getString(statements.getConsumerAgentColumn()),
                resultSet.getLong(statements.getSigningDateColumn()),
                resultSet.getString(statements.getAssetIdColumn()),
                resultSet.getString(statements.getPolicyColumn()));
    }

    /**
     * The policy is compared as its stored JSON text, not as a deserialized {@code Policy}: EDC's policy model
     * types ({@code Permission}, {@code Action}, {@code Constraint} and friends) do not override {@code equals},
     * so two structurally identical policies built independently, or round-tripped through JSON, are never equal
     * by object identity.
     */
    private record AgreementSnapshot(String id, String providerId, String consumerId, long contractSigningDate,
                                      String assetId, String policyJson) {
    }
}
