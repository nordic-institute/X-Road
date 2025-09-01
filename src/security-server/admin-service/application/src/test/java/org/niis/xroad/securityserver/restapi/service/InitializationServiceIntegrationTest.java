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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "spring.sql.init.mode=never"
})
class InitializationServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    private InitializationService initializationService;
    @Autowired
    private ServerConfService serverConfService;


    @Test
    void createInitialServerConfToEmptyDatabase() {
        ClientIdEntity ownerClientId = MemberIdEntity.create("INSTANCE", "CLASS", "MEMBER-CODE");
        String securityServerCode = "SS1";

        initializationService.createInitialServerConf(ownerClientId, securityServerCode);

        ServerConfEntity loadedServerConf = serverConfService.getServerConfEntity();
        assertThat(loadedServerConf.getServerCode()).isEqualTo(securityServerCode);
        assertThat(loadedServerConf.getOwner().getIdentifier()).isEqualTo(ownerClientId);
        assertThat(loadedServerConf.getClients()).size().isEqualTo(1);
    }


}



