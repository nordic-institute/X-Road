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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.ContractNegotiationStatements;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.eclipse.edc.sql.lease.spi.SqlLeaseContextBuilderProvider;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.time.Clock;

import static org.niis.xroad.edc.extension.store.contractnegotiation.XRoadContractNegotiationStoreExtension.EXTENSION_NAME;

/**
 * Takes over both duties of the stock {@code SqlContractNegotiationStoreExtension}, which is excluded from the
 * runtime via {@code xroad.edc.boot.excluded-service-extensions} so that this extension is the sole
 * {@link ContractNegotiationStore} provider: it constructs the stock SQL store, with stock Postgres dialect
 * statements and stock lease mechanics, wrapped in {@link XRoadContractNegotiationStore}; and it registers the
 * stock schema DDL with the SQL schema bootstrapper. The excluded extension's jar stays on the runtime classpath
 * so its store class, dialect statements and schema resource remain available to this extension.
 */
@Provides({ContractNegotiationStore.class})
@Extension(value = EXTENSION_NAME)
public class XRoadContractNegotiationStoreExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Contract Negotiation Store";

    private static final String CONTRACT_NEGOTIATION_SCHEMA_RESOURCE = "contract-negotiation-schema.sql";

    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE,
            key = "edc.sql.store.contractnegotiation.datasource")
    private String dataSourceName;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private Clock clock;

    @Inject(required = false)
    private ContractNegotiationStatements statements;

    @Inject
    private TypeManager typeManager;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    @Inject
    private SqlLeaseContextBuilderProvider leaseContextBuilderProvider;

    @Inject
    private LeaseStatements leaseStatements;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var statementImpl = resolveStatements();
        var leaseContextBuilder = leaseContextBuilderProvider.createContextBuilder(statementImpl.getContractNegotiationTable());
        var delegate = new SqlContractNegotiationStore(dataSourceRegistry, dataSourceName, transactionContext,
                typeManager.getMapper(), statementImpl, leaseContextBuilder, queryExecutor);

        context.registerService(ContractNegotiationStore.class, new XRoadContractNegotiationStore(delegate));

        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, CONTRACT_NEGOTIATION_SCHEMA_RESOURCE);
    }

    private ContractNegotiationStatements resolveStatements() {
        return statements != null ? statements : new PostgresDialectStatements(leaseStatements, clock);
    }
}
