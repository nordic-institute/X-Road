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
package org.niis.xroad.securityserver.restapi.scheduling;

import lombok.experimental.UtilityClass;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.impl.ownserver.OwnServerIdentitySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.mockito.Mockito.mock;

/**
 * Builds the {@link DataspacePublicationGlobalConfCheck} under test.
 */
@UtilityClass
class PublicationCheckTestFactory {

    DataspacePublicationGlobalConfCheck create(DsParticipantRepository dsParticipantRepository,
            ClientRepository clientRepository, GlobalConfProvider globalConfProvider,
            DataspaceDifferenceLog differenceLog, DataspaceReadinessPredicates readinessPredicates,
            DataspaceDidAuthority didAuthority, OwnSecurityServerResolver ownSecurityServerResolver,
            AdminServiceProperties adminServiceProperties, PlatformTransactionManager transactionManager) {
        return new DataspacePublicationGlobalConfCheck(dsParticipantRepository, clientRepository, globalConfProvider,
                differenceLog, readinessPredicates, didAuthority, ownSecurityServerResolver, adminServiceProperties,
                transactionManager);
    }

    /**
     * Builds the check with a real difference log, a real address resolver over a mocked identity source and a mocked
     * transaction manager, running on the given clock.
     */
    DataspacePublicationGlobalConfCheck create(DsParticipantRepository dsParticipantRepository,
            ClientRepository clientRepository, GlobalConfProvider globalConfProvider,
            DataspaceReadinessPredicates readinessPredicates, DataspaceDidAuthority didAuthority,
            AdminServiceProperties adminServiceProperties, Clock clock) {
        var check = create(dsParticipantRepository, clientRepository, globalConfProvider, new DataspaceDifferenceLog(),
                readinessPredicates, didAuthority,
                new OwnSecurityServerResolver(mock(OwnServerIdentitySource.class), globalConfProvider),
                adminServiceProperties, mock(PlatformTransactionManager.class));
        check.setClock(clock);
        return check;
    }
}
