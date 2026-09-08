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
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.sql.lease.spi.SqlLeaseContextBuilderProvider;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.edc.extension.store.contractnegotiation.schema.postgres.XRoadPostgresContractNegotiationStatements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadContractNegotiationStoreExtensionTest {

    @Test
    void initializeRegistersSoleConvergingStoreAndTakesOverSchemaDdl(ServiceExtensionContext context, ObjectFactory factory) {
        var leaseContextBuilderProvider = mock(SqlLeaseContextBuilderProvider.class);
        when(leaseContextBuilderProvider.createContextBuilder(any())).thenReturn(mock());
        var sqlSchemaBootstrapper = mock(SqlSchemaBootstrapper.class);
        var typeManager = mock(TypeManager.class);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());

        context.registerService(DataSourceRegistry.class, mock(DataSourceRegistry.class));
        context.registerService(TransactionContext.class, mock(TransactionContext.class));
        context.registerService(TypeManager.class, typeManager);
        context.registerService(QueryExecutor.class, mock(QueryExecutor.class));
        context.registerService(SqlSchemaBootstrapper.class, sqlSchemaBootstrapper);
        context.registerService(SqlLeaseContextBuilderProvider.class, leaseContextBuilderProvider);

        var extension = factory.constructInstance(XRoadContractNegotiationStoreExtension.class);
        extension.initialize(context);

        var registered = context.getService(ContractNegotiationStore.class);
        assertThat(registered).isInstanceOf(XRoadContractNegotiationStore.class);
        assertThat(registered).extracting("statements").isInstanceOf(XRoadPostgresContractNegotiationStatements.class);
        assertThat(registered).extracting("delegate").extracting("statements")
                .isInstanceOf(XRoadPostgresContractNegotiationStatements.class);

        verify(sqlSchemaBootstrapper).addStatementFromResource(DataSourceRegistry.DEFAULT_DATASOURCE, "contract-negotiation-schema.sql");
    }
}
