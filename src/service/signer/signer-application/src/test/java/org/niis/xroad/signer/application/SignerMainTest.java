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
package org.niis.xroad.signer.application;

import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.niis.xroad.signer.core.service.TokenKeyCertRequestWriteService;
import org.niis.xroad.signer.core.service.TokenKeyCertWriteService;
import org.niis.xroad.signer.core.service.TokenKeyWriteService;
import org.niis.xroad.signer.core.service.TokenWriteService;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.jpa.service.impl.TokenKeyCertRequestWriteServiceImpl;
import org.niis.xroad.signer.jpa.service.impl.TokenKeyCertWriteServiceImpl;
import org.niis.xroad.signer.jpa.service.impl.TokenKeyWriteServiceImpl;
import org.niis.xroad.signer.jpa.service.impl.TokenWriteServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Slf4j
@QuarkusTest
@TestProfile(SignerTestProfile.class)
class SignerMainTest {

    @Inject
    TokenLookup tokenLookup;

    @Inject
    TokenKeyCertRequestWriteService tokenKeyCertRequestWriteService;
    @Inject
    TokenKeyCertWriteService tokenKeyCertWriteService;
    @Inject
    TokenKeyWriteService tokenKeyWriteService;
    @Inject
    TokenWriteService tokenWriteService;

    @Test
    void testMain() {
        var result = tokenLookup.listTokens();
        log.info("Token lookup result: {}", result);
        assertEquals(1, result.size(), "Token lookup should not throw an exception");
    }

    @Test
    void testWriteServicesAvailableOnPrimaryNode() {
        assertInstanceOf(TokenKeyCertRequestWriteServiceImpl.class, ClientProxy.unwrap(tokenKeyCertRequestWriteService));
        assertInstanceOf(TokenKeyCertWriteServiceImpl.class, ClientProxy.unwrap(tokenKeyCertWriteService));
        assertInstanceOf(TokenKeyWriteServiceImpl.class, ClientProxy.unwrap(tokenKeyWriteService));
        assertInstanceOf(TokenWriteServiceImpl.class, ClientProxy.unwrap(tokenWriteService));
    }

}
